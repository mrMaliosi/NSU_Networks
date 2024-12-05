package main

import (
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"strings"
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
	// Блок 1. Этап установки соединения (Handshake)
	/*	Запрос клиента
	 * 	+----+----------+-----------------+
	 *	|VER | NMETHODS | METHODS         |
	 *	+----+----------+-----------------+
	 *
	 *	VER (1 байт): Версия протокола, для SOCKS5 это 0x05.
	 *	NMETHODS (1 байт): Количество поддерживаемых методов аутентификации.
	 *	METHODS (N байт): Список поддерживаемых методов аутентификации.
	 */
	var buf [256]byte
	_, err := clientConn.Read(buf[:])
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
	/*
	 * 	+----+----------+
	 *	|VER | NMETHOD  |
	 *	+----+----------+
	 *
	 *	VER (1 байт): Версия протокола {для SOCKS5 это 0x05}.
	 *	METHOD (1 байт): Выбранный метод аутентификации {0x00 - No authentication}
	 */
	clientConn.Write([]byte{0x05, 0x00})

	// Блок 2. Аутентификация (в нашем случае не требуется)

	// Блок 3. Запросы клиентов
	/*	Запрос клиента
	 *
	 *	+----+-----+-------+---------+---------------------+
	 *	|VER | CMD | RSV   | ATYP    | DST.ADDR            |
	 *	+----+-----+-------+---------+---------------------+
	 *
	 *	VER [1 байт]: Версия протокола {0x05}.
	 *	CMD [1 байт]: Команда (например, 0x01 — CONNECT, 0x02 — BIND, 0x03 — UDP ASSOCIATE).
	 *	RSV [1 байт]: Зарезервировано на будущее, должно быть 0x00.
	 *	ATYP [1 байт]: Тип адреса:
	 *		0x01 — IPv4.
	 *		0x03 — Доменное имя.
	 *		0x04 — IPv6.
	 *	DST.ADDR [зависит от ATYP]: Адрес назначения.
	 *	DST.PORT [2 байта]: Порт назначения.
	 */
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

	/* Отправляем успешный ответ клиенту
	 *	+----+-----+-------+---------+---------------------+
	 *	|VER | REP | RSV   | ATYP    | BND.ADDR            |
	 *	+----+-----+-------+---------+---------------------+
	 *
	 *	VER [1 байт]: Версия протокола {0x05}.
	 *	REP (1 байт): Код ответа (например, 0x00 — успешное выполнение, 0x01 — ошибка соединения)
	 *	RSV [1 байт]: Зарезервировано на будущее, должно быть 0x00.
	 *	ATYP [1 байт]: Тип адреса (указывается, куда подключён сервер):
	 *		0x01 — IPv4.
	 *		0x03 — Доменное имя.
	 *		0x04 — IPv6.
	 *	DST.ADDR [зависит от ATYP]: Адрес, на который сервер привязан.
	 *	DST.PORT [2 байта]: Порт, на который сервер привязан.
	 */
	//Отправляем адрес и порт сервера как 0.0.0.0, так как мы не обязаны говорить свой реальный адрес
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
		log.Println(LOG + "domain: " + req.addr)
		log.Println(LOG + "ip: " + ip)
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
	// Строим DNS-запрос для получения A-записи (тип 0x01)
	query := make([]byte, 12+len(domain)+4+4)

	// ID запроса (2 байта)
	query[0] = 0x12
	query[1] = 0x34

	// Флаги (2 байта)
	/*
	 *	Состоит из:
	 *
	 *	QR - [1 бит] - запрос это (0) или ответ(1)
	 *	Opcode - [4 бит] - тип запроса:
	 *						0: стандартный
	 *						1: инверсный
	 *						2: запрос статуса сервера
	 *						3-15: зарезервированы на будущее
	 *	TC - [1 бит] - указывает на обрезанное сообщение (у нас сообщение короткое, так что 0)
	 *	RD - [1 бит] - Однобитный флаг, указывающий на желательную рекурсию. Если DNS-сервер,
	 *					которому мы отправляем вопрос, не знает ответа на него, он может
	 *					рекурсивно опросить другие DNS-серверы. Мы хотим активировать рекурсию,
	 *					так что укажем 1.
	 *	RA - [1 бит] - Рекурсивная доступность. Указывает, поддерживает ли сервер рекурсивный запрос.
	 *	Z - [3 бита] - зарезервировано на будущее
	 *	RCODE - [4 бит] - указывает статусответа
	 */
	query[2] = 0x01 // стандартный запрос
	query[3] = 0x00

	// Число вопросов (2 байта)
	query[4] = 0x00
	query[5] = 0x01

	// Число ответов, записей в авторитете и дополнительных записей (2 байта)
	query[6] = 0x00
	query[7] = 0x00
	query[8] = 0x00
	query[9] = 0x00

	// Добавляем имя домена в поле QNAME (каждая часть начинается с байта с длиной этой части)
	/*
	 *	ПЕРВЫЙ байт - длина подаписи
	 *	ВТОРОЙ и до + ПЕРВЫЙ - 	сама подзапись
	 *	... - и так далее пока не запишем весь домен
	 *
	 *	В конце: нулевой байт.
	 */
	offset := 12
	parts := strings.Split(domain, ".")
	for _, part := range parts {
		query[offset] = byte(len(part)) // Длина части
		offset++
		copy(query[offset:], part) // Само имя части
		offset += len(part)
	}

	// Завершаем имя домена байтом с длиной 0
	query[offset] = 0

	// Тип (A-запись, 0x0001)
	// QTYPE - тип A-записи {IPv4}
	query[offset+1] = 0x00
	query[offset+2] = 0x01

	// Класс (IN, 0x0001)
	// QCLASS - класс записи {IN (Internet)}
	query[offset+3] = 0x00
	query[offset+4] = 0x01

	//fmt.Println(query)

	return query
}

func extractIPAddressFromDNSResponse(response []byte) string {
	//fmt.Println(response)
	// Проверка длины ответа, чтобы не выйти за границы массива
	if len(response) < 12 {
		return ""
	}

	// Пропускаем заголовок ответа и раздел с вопросами (если он есть)
	offset := 12
	for offset < len(response) {
		labelLength := int(response[offset])
		offset++
		if labelLength == 0 {
			break
		}
		offset += labelLength
	}

	// Пропускаем тип записи (QTYPE) и класс записи (QCLASS)
	offset += 4 // QTYPE (2 байта) + QCLASS (2 байта)

	// Длина данных (первый байт)
	//dataLength := int(response[offset])<<8 + int(response[offset+1])
	offset += 12

	// Проверка, что длина данных подходит и извлечение IP-адреса
	if len(response) >= offset+4 {
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
