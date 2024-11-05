package main

import (
	"bufio"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"strconv"
	"time"
)

const (
	RESET   = "\033[0m"
	RED     = "\033[31m"
	GREEN   = "\033[32m"
	YELLOW  = "\033[33m"
	BLUE    = "\033[34m"
	PURPLE  = "\033[35m"
	CYAN    = "\033[36m"
	WHITE   = "\033[97m"
	ERROR   = RED + "<ERROR>: " + RESET
	SUCCESS = GREEN + "<SUCCESS>: " + RESET
	LOG     = YELLOW + "<LOG>: " + RESET
)

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Использование: go run server.go <порт>")
		return
	}

	port := os.Args[1]

	listener, err := net.Listen("tcp", ":"+port)
	if err != nil {
		fmt.Println(ERROR+"Ошибка запуска сервера.\n"+ERROR, err)
		return
	}
	defer listener.Close()

	fmt.Println("Сервер запущен на порту " + YELLOW + port + RESET)

	for {
		conn, err := listener.Accept()
		if err != nil {
			fmt.Println(ERROR+"Ошибка подключения.\n"+ERROR, err)
			continue
		}
		go handleConnection(conn)
	}
}

func handleConnection(conn net.Conn) {
	defer conn.Close()

	reader := bufio.NewReader(conn)

	// Чтение имени файла
	fileName, err := reader.ReadString('\n')
	if err != nil {
		fmt.Println(ERROR+"Ошибка чтения имени файла.\n"+ERROR, err)
		return
	}
	fileName = fileName[:len(fileName)-1] // Удаление символа новой строки

	// Чтение размера файла
	fileSizeStr, err := reader.ReadString('\n')
	if err != nil {
		fmt.Println(ERROR+"Ошибка чтения размера файла.\n"+ERROR, err)
		return
	}

	// Чтение размера буфера
	bufferSizeRaw, err := reader.ReadString('\n')
	if err != nil {
		fmt.Println(ERROR+"Ошибка чтения размера буфера.\n"+ERROR, err)
		return
	}
	bufferSizeRaw = bufferSizeRaw[:len(fileSizeStr)-1]
	bufferSize, err := strconv.Atoi(bufferSizeRaw)
	if err != nil {
		fmt.Println(ERROR+"Ошибка преобразования размера буфера.\n"+ERROR, err)
		return
	}

	fileSizeStr = fileSizeStr[:len(fileSizeStr)-1] // Удаление символа новой строки
	fileSize, err := strconv.Atoi(fileSizeStr)
	if err != nil {
		fmt.Println(ERROR+"Ошибка преобразования размера файла.\n"+ERROR, err)
		return
	}

	// Создание директории uploads, если она не существует
	uploadsDir := filepath.Join(filepath.Dir(os.Args[0]), "uploads")
	if err := os.MkdirAll(uploadsDir, os.ModePerm); err != nil {
		fmt.Println(ERROR+"Ошибка создания директории uploads.\n"+ERROR, err)
		return
	}

	// Создание файла
	file, err := os.Create(filepath.Join(uploadsDir, fileName))
	if err != nil {
		fmt.Println(ERROR+"Ошибка создания файла: \n"+ERROR, err)
		return
	}
	defer file.Close()

	buffer := make([]byte, bufferSize)

	totalBytesRead := int64(0)
	startTime := time.Now()
	previousBytes := int64(0)

	done := make(chan struct{}) // Канал для завершения горутины

	go func() {
		ch := 0
		sumElapsedTime := float64(0)
		sumBytesRecivedTime := float64(0)
		for {
			select {
			case <-time.After(3 * time.Second):
				currentTime := time.Now()
				elapsedTime := currentTime.Sub(startTime).Seconds()
				bytesReceived := totalBytesRead - previousBytes
				previousBytes = totalBytesRead
				sumElapsedTime += elapsedTime
				sumBytesRecivedTime += float64(bytesReceived)
				ch = 1

				if elapsedTime > 0 {
					speed := float64(bytesReceived) / elapsedTime // Байт/с
					fmt.Printf(LOG+"Мгновенная скорость: %.2f байт/с\n", speed)
					fmt.Printf(LOG+"Скорость за сеанс: %.2f байт/с\n", sumBytesRecivedTime/sumElapsedTime)
				}
			case <-done:
				if ch == 0 {
					currentTime := time.Now()
					elapsedTime := currentTime.Sub(startTime).Seconds()
					bytesReceived := totalBytesRead - previousBytes
					previousBytes = totalBytesRead

					if elapsedTime > 0 {
						speed := float64(bytesReceived) / elapsedTime // Байт/сек
						fmt.Printf(LOG+"Мгновенная скорость: %.2f байт/с\n", speed)
						fmt.Printf(LOG+"Скорость за сеанс: %.2f байт/с\n", speed)
					}
				}
				return
			}
		}
	}()

	for totalBytesRead < int64(fileSize) {
		n, err := reader.Read(buffer)
		if err != nil {
			if err == io.EOF {
				break // Конец файла
			}
			fmt.Println(ERROR+"Ошибка чтения содержимого файла: ", err)
			return
		}

		// Запись в файл
		_, err = file.Write(buffer[:n])
		if err != nil {
			fmt.Println(ERROR+"Ошибка записи файла: ", err)
			return
		}

		totalBytesRead += int64(n)
	}

	// Сигнализируем горутине о завершении
	close(done)

	fmt.Printf(SUCCESS+"Файл %s успешно загружен.\n", fileName)
}
