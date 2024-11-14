package main

import (
	"bufio"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strconv"
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
	SUCCESS = GREEN + "<SUCCESS>:" + RESET
)

const bufferSize = 1024

func main() {
	if len(os.Args) < 4 {
		fmt.Println("Использование: go run client.go <DNS-имя или IP-адрес> <порт> <путь к файлу>")
		return
	}

	serverAddr := os.Args[1] + ":" + os.Args[2]
	filename := os.Args[3]
	handleConnection(serverAddr, filename)
}

func handleConnection(serverAddr string, filename string) {
	file, err := os.Open(filename)
	if err != nil {
		fmt.Println(ERROR+"ШОЙГУ! ГДЕ ФАЙЛ?!!!\n"+ERROR, err)
		return
	}
	defer file.Close()

	conn, err := net.Dial("tcp", serverAddr)
	if err != nil {
		fmt.Println(ERROR+"Шеф, всё пропало. Сервер не отвечает.\n"+ERROR, err)
		return
	}
	defer conn.Close()

	fileInfo, err := file.Stat()
	if err != nil {
		fmt.Println(ERROR+"Невероятно, но мы не смогли получить информацию о файле.\n"+ERROR, err)
		return
	}

	fileName := filepath.Base(filename)
	fileSize := strconv.Itoa(int(fileInfo.Size()))

	writer := bufio.NewWriter(conn)

	// Отправка имени файла
	_, err = writer.WriteString(fileName + "\n")
	if err != nil {
		fmt.Println(ERROR+"Ошибка отправки имени файла.\n"+ERROR, err)
		return
	}

	// Отправка размера файла
	_, err = writer.WriteString(fileSize + "\n")
	if err != nil {
		fmt.Println(ERROR+"Ошибка отправки размера файла\n"+ERROR, err)
		return
	}

	// Отправка размера буфера
	_, err = writer.WriteString(strconv.Itoa(bufferSize) + "\n")
	if err != nil {
		fmt.Println(ERROR+"Ошибка отправки размера файла\n"+ERROR, err)
		return
	}

	buffer := make([]byte, bufferSize)

	for {
		n, err := file.Read(buffer)
		if err != nil {
			if err == io.EOF {
				break // Конец файла
			}
			fmt.Println(ERROR+"Ошибка чтения файла:"+ERROR, err)
			return
		}

		_, err = writer.Write(buffer[:n])
		if err != nil {
			fmt.Println(ERROR+"Ошибка записи данных: %w\n"+ERROR, err)
			return
		}
	}

	err = writer.Flush()
	if err != nil {
		fmt.Println(ERROR+"Ошибка завершения отправки данных\n"+ERROR, err)
		return
	}

	fmt.Printf(SUCCESS+" Файл %s успешно отправлен.\n", fileName)
}
