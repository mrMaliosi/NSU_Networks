package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
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

func callMistralAPI(url string, payload RequestPayload) (ResponsePayload, error) {
	apiKey := "Jo3NjDDc5biDJqWNQ8FR1jLPodgkt5QK"
	jsonData, err := json.Marshal(payload)
	if err != nil {
		return ResponsePayload{}, err
	}
	fmt.Printf(LOG+"Request: %+v\n", payload)

	req, err := http.NewRequest("POST", url, bytes.NewBuffer(jsonData))
	if err != nil {
		log.Fatalf(ERROR+"Error creating request: %v", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+apiKey)

	client := &http.Client{}
	resp, err := client.Do(req)
	if err != nil {
		log.Fatalf(ERROR+"Error sending POST request: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return ResponsePayload{}, fmt.Errorf(ERROR+"Unexpected status: %s", resp.Status)
	}

	var response ResponsePayload
	err = json.NewDecoder(resp.Body).Decode(&response)
	if err != nil {
		return ResponsePayload{}, err
	}

	return response, nil
}

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Использование: go run main.go \"Текст запроса\"")
		return
	}

	url := "https://api.mistral.ai/v1/chat/completions"
	payload := RequestPayload{
		Model: "mistral-small-latest",
		Messages: []Message{
			{Role: "user", Content: os.Args[1]},
		},
	}

	response, err := callMistralAPI(url, payload)
	if err != nil {
		log.Fatalf(ERROR+"Error calling Mistral API: %v", err)
	}

	//fmt.Printf(SUCCESS+"Response: %+v\n", response)

	for i, choice := range response.Choices {
		fmt.Printf(SUCCESS+"Response %d: \n%s", i+1, choice.Message.Content)
	}
}
