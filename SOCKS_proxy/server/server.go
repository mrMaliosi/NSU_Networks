package main

import (
	"fmt"
	"io"
	"log"
	"net"
	"os"
)

// go get -u golang.org/x/net/proxy
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

const (
	dnsServerAddress = "8.8.8.8:53" // DNS-сервер Google
)

type dnsRequest struct {
	conn   net.Conn
	client net.Conn
	addr   string
	port   int
}

func handleClientRequest(clientConn net.Conn, dnsConn net.Conn, dnsRequests chan dnsRequest) {
	// Блок 1. Чтение версии SOCKS5
	var buf [256]byte
	_, err := clientConn.Read(buf[:]) // Преобразуем buf в срез
	if err != nil {
		log.Println(ERROR+"Error reading from client:", err)
		return
	}

	// Проверка версии SOCKS5
	if buf[0] != 0x05 {
		log.Println(ERROR + "Unsupported SOCKS version")
		clientConn.Close()
		return
	}

	// Отправка ответа (метод аутентификации "no authentication required")
	clientConn.Write([]byte{0x05, 0x00})

	// Блок 2. Ожидание команды клиента
	_, err = clientConn.Read(buf[:])
	if err != nil {
		log.Println(ERROR+"Error reading client request:", err)
		clientConn.Close()
		return
	}

	// Только команда 0x01 (CONNECT) поддерживается
	if buf[1] != 0x01 {
		log.Println(ERROR+"Unsupported command:", buf[1])
		clientConn.Write([]byte{0x05, 0x07, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})
		clientConn.Close()
		return
	}

	// Получение удалённого хоста и порта
	addressType := buf[3]
	var address string
	var port int
	switch addressType {
	case 0x01: // IPv4
		address = fmt.Sprintf("%d.%d.%d.%d", buf[4], buf[5], buf[6], buf[7])
		port = int(buf[8])<<8 + int(buf[9])
	case 0x03: // Domain name
		domainLength := int(buf[4])
		address = string(buf[5 : 5+domainLength])
		port = int(buf[5+domainLength])<<8 + int(buf[6+domainLength])

		// Отправляем запрос на резолвинг доменного имени
		dnsRequests <- dnsRequest{
			conn:   dnsConn,
			client: clientConn,
			addr:   address,
			port:   port,
		}

		return
	default:
		log.Println(ERROR + "Unsupported address type")
		clientConn.Write([]byte{0x05, 0x08, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})
		clientConn.Close()
		return
	}

	// Подключаемся к удаленному хосту
	serverConn, err := net.Dial("tcp", fmt.Sprintf("%s:%d", address, port))
	if err != nil {
		log.Println(ERROR+"Error connecting to remote host:", err)
		clientConn.Write([]byte{0x05, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})
		clientConn.Close()
		return
	}

	// Отправляем успешный ответ клиенту
	clientConn.Write([]byte{0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})

	// Создаем каналы для двусторонней передачи данных
	go transferData(clientConn, serverConn)
	go transferData(serverConn, clientConn)
}

// Функция для перенаправления данных между клиентом и сервером
func transferData(src, dst net.Conn) {
	_, err := io.Copy(dst, src)
	if err != nil {
		log.Println(ERROR+"Error copying data:", err)
	}
	src.Close()
	dst.Close()
}

func resolveDomainName(dnsConn net.Conn, dnsRequests chan dnsRequest) {
	for req := range dnsRequests {
		// Строим DNS-запрос
		query := buildDNSQuery(req.addr)
		_, err := dnsConn.Write(query)
		if err != nil {
			log.Println(ERROR+"Error sending DNS query:", err)
			req.client.Close()
			continue
		}

		// Ждем ответа от DNS-сервера
		buf := make([]byte, 512)
		_, err = dnsConn.Read(buf)
		if err != nil {
			log.Println(ERROR+"Error reading DNS response:", err)
			req.client.Close()
			continue
		}

		// Извлекаем IP-адрес из ответа
		ip := extractIPAddressFromDNSResponse(buf)
		if ip == "" {
			log.Println(ERROR + "DNS query failed: no A record found")
			req.client.Close()
			continue
		}

		// Подключаемся к серверу по полученному IP-адресу
		serverConn, err := net.Dial("tcp", fmt.Sprintf("%s:%d", ip, req.port))
		if err != nil {
			log.Println(ERROR+"Error connecting to remote host:", err)
			req.client.Write([]byte{0x05, 0x01, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})
			req.client.Close()
			continue
		}

		req.client.Write([]byte{0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00})

		go transferData(req.client, serverConn)
		go transferData(serverConn, req.client)
	}
}

func buildDNSQuery(domain string) []byte {
	// Построение стандартного DNS-запроса для A-записи
	query := make([]byte, 12+len(domain)+4)
	query[0] = 0x01 // ID запроса
	query[1] = 0x00 // стандартный запрос
	query[2] = 0x00
	query[3] = 0x01 // количество вопросов
	query[4] = 0x00
	query[5] = 0x00
	query[6] = 0x00
	query[7] = 0x00 // количество ответов
	query[8] = 0x00
	query[9] = 0x00
	query[10] = 0x00
	query[11] = 0x00

	// Запросим A-запись для домена
	offset := 12
	for _, part := range domain {
		query[offset] = byte(part) // Присваиваем длину строки
		offset += 1                // Обновляем offset
	}

	// Добавляем тип A (0x01)
	query[offset] = 0x00
	query[offset+1] = 0x01

	// Добавляем класс IN (0x01)
	query[offset+2] = 0x00
	query[offset+3] = 0x01

	println(LOG + string(query))

	return query
}

func extractIPAddressFromDNSResponse(response []byte) string {
	// Извлекаем первый IP-адрес из DNS-ответа
	if len(response) < 12 {
		return ""
	}

	// Ищем позицию, где начинается ответ
	offset := 12
	for response[offset] != 0 {
		offset += int(response[offset]) + 1
	}
	offset += 5

	// Должны быть 4 байта IP-адреса в ответе
	if len(response) > offset+3 {
		return fmt.Sprintf("%d.%d.%d.%d", response[offset], response[offset+1], response[offset+2], response[offset+3])
	}

	return ""
}

func main() {
	if len(os.Args) < 2 {
		log.Fatal(ERROR + "Usage: go run proxy.go <port>")
	}

	port := os.Args[1]
	listener, err := net.Listen("tcp", fmt.Sprintf(":%s", port))
	if err != nil {
		log.Fatal(ERROR+"Error listening on port", port, err)
	}
	defer listener.Close()

	log.Println(LOG+"SOCKS5 proxy server listening on port", port)

	// Создаем сокет для отправки DNS-запросов
	dnsConn, err := net.Dial("udp", dnsServerAddress)
	if err != nil {
		log.Fatal(ERROR+"Error connecting to DNS server:", err)
	}
	defer dnsConn.Close()

	dnsRequests := make(chan dnsRequest)

	// Запуск резолвинга доменных имен
	go resolveDomainName(dnsConn, dnsRequests)

	// Главный цикл прокси-сервера
	for {
		clientConn, err := listener.Accept()
		if err != nil {
			log.Println(ERROR+"Error accepting connection:", err)
			continue
		}

		go handleClientRequest(clientConn, dnsConn, dnsRequests)
	}
}
