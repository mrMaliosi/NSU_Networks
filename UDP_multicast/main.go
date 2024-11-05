package main

import (
	"fmt"
	"net"
	"os"
	"strings"
	"sync"
	"time"
)

const (
	multicastPort = "9999"
	interval      = 5 * time.Second
	timeout       = 10 * time.Second
)

var (
	liveCopies = make(map[string]time.Time)
	mu         sync.Mutex
)

func main() {
	if len(os.Args) < 2 {
		fmt.Println("Usage: go run main.go <multicast-group>")
		os.Exit(1)
	}

	multicastGroup := os.Args[1]
	addr, err := net.ResolveUDPAddr("udp", net.JoinHostPort(multicastGroup, multicastPort))
	if err != nil {
		fmt.Println("Error resolving multicast address:", err)
		os.Exit(1)
	}

	conn, err := net.ListenMulticastUDP("udp", nil, addr)
	if err != nil {
		fmt.Println("Error joining multicast group:", err)
		os.Exit(1)
	}
	defer conn.Close()

	go receiveMessages(conn)
	go sendMessages(addr)

	for {
		time.Sleep(interval)
		printLiveCopies()
	}
}

func receiveMessages(conn *net.UDPConn) {
	buf := make([]byte, 256)
	for {
		n, src, err := conn.ReadFromUDP(buf)
		if err != nil {
			fmt.Println("Error receiving message:", err)
			continue
		}

		mu.Lock()
		liveCopies[src.String()] = time.Now()
		mu.Unlock()

		fmt.Printf("Received message from %s: %s\n", src.String(), strings.TrimSpace(string(buf[:n])))
	}
}

func sendMessages(addr *net.UDPAddr) {
	conn, err := net.DialUDP("udp", nil, addr)
	if err != nil {
		fmt.Println("Error sending message:", err)
		os.Exit(1)
	}
	defer conn.Close()

	for {
		_, err := conn.Write([]byte("Hello from " + conn.LocalAddr().String()))
		if err != nil {
			fmt.Println("Error sending message:", err)
		}
		time.Sleep(interval)
	}
}

func printLiveCopies() {
	mu.Lock()
	defer mu.Unlock()

	now := time.Now()
	for ip, lastSeen := range liveCopies {
		if now.Sub(lastSeen) > timeout {
			delete(liveCopies, ip)
		}
	}

	fmt.Println("Live copies:")
	for ip := range liveCopies {
		fmt.Println(ip)
	}
}
