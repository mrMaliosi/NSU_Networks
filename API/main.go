package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"sync"

	"github.com/joho/godotenv"
)

const (
	RESET  = "\033[0m"
	RED    = "\033[31m"
	GREEN  = "\033[32m"
	YELLOW = "\033[33m"
	BLUE   = "\033[34m"
	PURPLE = "\033[35m"
	CYAN   = "\033[36m"
	WHITE  = "\033[97m"

	ERROR   = RED + "<ERROR>: " + RESET
	SUCCESS = GREEN + "<SUCCESS>: " + RESET
	LOG     = YELLOW + "<LOG>: " + RESET
)

const STOP_MACHINE = "STOP MACHINE!"

type Message struct {
	Role    string `json:"role"`
	Content string `json:"content"`
}

type RequestPayload struct {
	Model    string    `json:"model"`
	Messages []Message `json:"messages"`
}

type Usage struct {
	PromptTokens     int `json:"prompt_tokens"`
	CompletionTokens int `json:"completion_tokens"`
	TotalTokens      int `json:"total_tokens"`
}

type Choice struct {
	Message struct {
		Role    string `json:"role"`
		Content string `json:"content"`
	} `json:"message"`
}

type ResponsePayload struct {
	ID      string   `json:"id"`
	Object  string   `json:"object"`
	Model   string   `json:"model"`
	Usage   Usage    `json:"usage"`
	Created int64    `json:"created"`
	Choices []Choice `json:"choices"`
}

type JokeResponse struct {
	IconURL string `json:"icon_url"`
	ID      string `json:"id"`
	URL     string `json:"url"`
	Value   string `json:"value"`
}

var MISTRAL_API_KEY string

// Функция init выполняется при старте программы
func init() {
	if err := godotenv.Load(); err != nil {
		log.Fatal("Error loading .env file")
	}

	// Попытаться получить значение MISTRAL_API_KEY из переменных окружения
	MISTRAL_API_KEY = os.Getenv("MISTRAL_API_KEY")

	// Если API_KEY не установлен, вызвать panic
	if MISTRAL_API_KEY == "" {
		log.Panic("MISTRAL_API_KEY is not set. The application cannot start without it.")
	}
}

/*
func mistralLoaderIndicator(systemInputChan chan bool) {
	indicators := []string{"\\", "|", "/", "-"}
	i := 0
	for {
		select {
		case <-systemInputChan: // Получаем сигнал для остановки горутины
			return
		default:
			// Очистка строки в консоли, чтобы заменить предыдущий символ
			fmt.Print("\r" + "Processing request to Mistral: " + indicators[i])
			i = (i + 1) % len(indicators)
			time.Sleep(500 * time.Millisecond)
		}
	}
}
*/

func callMistralAPI(url string, payload RequestPayload, userInputChan chan string, wg *sync.WaitGroup) {
	defer wg.Done() // Уменьшаем счётчик в WaitGroup после завершения горутины

	jsonData, err := json.Marshal(payload)
	if err != nil {
		log.Println(ERROR+"Error marshaling JSON:", err)
		return
	}
	fmt.Printf(LOG+"Request: %+v\n", payload)
	//systemInputChan := make(chan bool)
	//go mistralLoaderIndicator(systemInputChan)

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		log.Fatalf(ERROR+"Error creating request: %v", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+MISTRAL_API_KEY)

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		log.Fatalf(ERROR+"Error sending POST request: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		log.Printf(ERROR+"Unexpected status: %s\n", resp.Status)
		return
	}

	var response ResponsePayload
	err = json.NewDecoder(resp.Body).Decode(&response)
	if err != nil {
		log.Println(ERROR+"Error decoding response:", err)
		return
	}

	userInputChan <- STOP_MACHINE
	//systemInputChan <- true
	// Выводим результаты
	for i, choice := range response.Choices {
		fmt.Printf(SUCCESS+"Response %d: \n%s\n", i+1, choice.Message.Content)
	}
}

func getChuckNorrisJoke(wg *sync.WaitGroup) {
	defer wg.Done()
	// URL API
	url := "https://api.chucknorris.io/jokes/random"

	// Выполняем GET запрос
	resp, err := http.Get(url)
	if err != nil {
		fmt.Printf(ERROR+"Не удалось выполнить запрос: %v", err)
	}
	defer resp.Body.Close()

	// Чтение ответа
	body, err := io.ReadAll(resp.Body)
	if err != nil {
		fmt.Printf(ERROR+"Не удалось прочитать тело ответа: %v", err)
	}

	// Проверяем статус-код
	if resp.StatusCode != http.StatusOK {
		fmt.Printf(ERROR+"Неудачный статус код: %d", resp.StatusCode)
	}

	// Парсим JSON ответ
	var joke JokeResponse
	err = json.Unmarshal(body, &joke)
	if err != nil {
		fmt.Printf(ERROR+"ошибка при парсинге JSON: %v", err)
	}

	fmt.Printf("Шутка: %s\n", joke.Value)
	fmt.Printf("Изображение Чака Норриса: %s\n", joke.IconURL)
}

func startInputChannel(userInputChan chan string) {
	reader := bufio.NewReader(os.Stdin)
	fmt.Print("Вы ввели запрос к MistralAI.\nЖдать его долго и скучно, поэтому пока он грузится можете ввести 'yes' для получения шутки про Чака Норриса.\n")
	input, _ := reader.ReadString('\n')
	userInputChan <- input
}

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Использование: go run main.go \"Текст запроса\"")
		return
	}

	// Создаём канал для ввода пользователя
	userInputChan := make(chan string)

	// Создаём WaitGroup чтобы гарантировать закрытие горутин
	var wg sync.WaitGroup
	wg.Add(1)

	// Запускаем горутину для чтения пользовательского ввода
	go startInputChannel(userInputChan)

	url := "https://api.mistral.ai/v1/chat/completions"
	payload := RequestPayload{
		Model: "mistral-small-latest",
		Messages: []Message{
			{Role: "user", Content: os.Args[1]},
		},
	}

	// Запускаем асинхронный запрос в Mistral
	go callMistralAPI(url, payload, userInputChan, &wg)

	dummyCount := 0
	userInput := <-userInputChan
	for {
		if userInput == "ye\n" {
			fmt.Println("СМЕХ!")
			wg.Add(1)
			go getChuckNorrisJoke(&wg)
		} else if userInput == STOP_MACHINE {
			break
		} else {
			if dummyCount == 0 {
				fmt.Println("Ну вот, чудесно. Они мне прислали И-ДИ-ОТА! Слушай сюда! Ещё раз ЧИ-ТА-ЕШЬ инструкцию к программе и возвращаешься сюда, чтобы получить шутку про Чака Нориса! СВОБОДЕН!")
				dummyCount += 1
			} else {
				fmt.Println("Ну вот, чудесно. Они мне прислали И-ДИ-ОТА! Слушай сюда! Ещё раз ЧИ-ТА-ЕШЬ инструкцию к программе и возвращаешься сюда, чтобы получить шутку про Чака Нориса! СВОБОДЕН!")
			}
		}

		// Ожидание нового ввода
		userInput = <-userInputChan
	}

	// Дожидаемся завершения всех горутин
	wg.Wait()
	close(userInputChan)
}
