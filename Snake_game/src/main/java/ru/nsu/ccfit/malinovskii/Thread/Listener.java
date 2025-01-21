package ru.nsu.ccfit.malinovskii.Thread;

import ru.nsu.ccfit.malinovskii.Model.Context.NetworkContext;
import ru.nsu.ccfit.malinovskii.Model.Context.PlayerContext;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.function.Consumer;

public class Listener {
    NetworkContext networkContext = NetworkContext.getContext();
    public static final String MULTICAST_ADDRESS = NetworkContext.getContext().getMulticastAddress();
    public static final int MULTICAST_PORT = NetworkContext.getContext().getMulticastPort();
    private Thread messageReceiverLoop;
    private MulticastSocket multicastSocket;
    private final Consumer<SnakesProto.GameMessage.AnnouncementMsg> gameAnnouncementHandler;
    private Consumer<SnakesProto.GameMessage.StateMsg> gameStateHandler;
    private DatagramSocket socket;

    private InetAddress masterAddres;
    private int masterPort;

    PlayerContext playerContext;

    // Конструктор принимает метод обратного вызова
    public Listener(Consumer<SnakesProto.GameMessage.AnnouncementMsg> gameAnnouncementHandler) {
        this.gameAnnouncementHandler = gameAnnouncementHandler;

        try {
            InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
            multicastSocket = new MulticastSocket(MULTICAST_PORT);
            multicastSocket.joinGroup(group);

            this.messageReceiverLoop = new Thread(this::receiveMessageLoop);
        } catch (IOException e) {
            System.err.println("Error initializing multicast socket: " + e.getMessage());
        }
    }

    public void addGameStateHandler(Consumer<SnakesProto.GameMessage.StateMsg> gameStateHandler) {
        this.gameStateHandler = gameStateHandler;
    }

    public void recieveClientMsgLoop() {
        this.socket = networkContext.getSocket();
        byte[] buffer = new byte[4096]; // Буфер для получения данных
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (!Thread.currentThread().isInterrupted()) { // Проверяем, что поток не был прерван
            try {
                // Получаем сообщение
                socket.receive(packet);

                // Декодируем сообщение
                SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(
                        Arrays.copyOf(packet.getData(), packet.getLength())
                );

                // Обработка сообщений
                if (message.hasState()) {
                    System.out.println("[LOG]: Received StateMsg.");
                    SnakesProto.GameMessage.StateMsg stateMsg = message.getState();

                    // Передаем сообщение через gameStateHandler
                    if (gameStateHandler != null) {
                        gameStateHandler.accept(stateMsg);
                    } else {
                        System.out.println("[WARN]: No handler set for StateMsg.");
                    }
                } else if (message.hasRoleChange()) {
                    System.out.println("[LOG]: Received RoleChangeMsg.");
                    long msgSeq = message.getMsgSeq();
                    SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg = message.getRoleChange();

                    masterAddres = packet.getAddress();
                    masterPort = packet.getPort();

                    // Обработка RoleChangeMsg
                    handleRoleChange(roleChangeMsg, packet.getAddress(), packet.getPort(), msgSeq);
                } else if (message.hasPing()) {
                    System.out.println("[LOG]: Received PingMsg.");
                    long msgSeq = message.getMsgSeq();

                    // Отправляем AckMsg в ответ на PingMsg
                    sendAckMessage(packet.getAddress(), packet.getPort(), msgSeq);
                } else {
                    System.out.println("[INFO]: Received message, but not StateMsg, RoleChangeMsg, or PingMsg.");
                }
            } catch (IOException e) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("[INFO]: Listener thread interrupted.");
                    break; // Завершаем цикл, если поток был прерван
                }
                System.err.println("[ERROR]: Error while receiving message: " + e.getMessage());
            }
        }
    }

    public InetAddress getMasterAddress(){
        return masterAddres;
    }

    public int getMasterPort(){
        return masterPort;
    }


    // Метод для обработки RoleChangeMsg
    private void handleRoleChange(SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg, InetAddress address, int port, long msgSeq) {
        // Логируем изменение роли
        System.out.println("[INFO]: RoleChangeMsg received. New role: " + roleChangeMsg.getReceiverRole());

        playerContext = PlayerContext.getPlayerContext();

        // Обновляем роль локального игрока
        playerContext.setPlayerRole(roleChangeMsg.getReceiverRole());
        System.out.println("[INFO]: Updated local player role to: " + roleChangeMsg.getReceiverRole());

        // Отправляем подтверждение (AckMsg) отправителю RoleChangeMsg
        sendAckMessage(address, port, msgSeq);
    }

    // Метод для отправки AckMsg
    private void sendAckMessage(InetAddress address, int port, long msgSeq) {
        try {
            // Получаем адрес и порт отправителя
            InetAddress senderAddress = address;
            int senderPort = port;

            // Создаем сообщение AckMsg
            SnakesProto.GameMessage ackMessage = SnakesProto.GameMessage.newBuilder()
                    .setAck(SnakesProto.GameMessage.AckMsg.getDefaultInstance())
                    .setMsgSeq(msgSeq) // Указываем тот же Seq, что и в RoleChangeMsg
                    .build();

            // Кодируем сообщение в байты
            byte[] messageBytes = ackMessage.toByteArray();

            // Отправляем сообщение
            DatagramPacket ackPacket = new DatagramPacket(
                    messageBytes,
                    messageBytes.length,
                    senderAddress,
                    senderPort
            );

            socket.send(ackPacket);
            System.out.println("[INFO]: AckMsg sent for RoleChangeMsg with Seq: " + msgSeq);
        } catch (IOException e) {
            System.err.println("[ERROR]: Failed to send AckMsg: " + e.getMessage());
        }
    }

    private void receiveMessageLoop() {
        byte[] buffer = new byte[4096]; // Буфер для получения данных
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            try {
                if (!Thread.currentThread().isInterrupted()) {
                    // Получаем сообщение
                    multicastSocket.receive(packet);

                    // Декодируем сообщение
                    SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(
                            Arrays.copyOf(packet.getData(), packet.getLength())
                    );

                    // Проверяем тип сообщения
                    if (message.hasAnnouncement()) {
                        SnakesProto.GameMessage.AnnouncementMsg announcement = message.getAnnouncement();

                        // Передаём сообщение в метод обратного вызова
                        gameAnnouncementHandler.accept(announcement);
                    }
                } else {
                    return;
                }
            } catch (IOException e) {
                System.err.println("Error receiving multicast message: " + e.getMessage());
            }
        }
    }

    public void start() {
        if (!messageReceiverLoop.isAlive()) {
            messageReceiverLoop.start();
        }
    }

    public void stop() {
        messageReceiverLoop.interrupt();
        try {
            multicastSocket.leaveGroup(InetAddress.getByName(MULTICAST_ADDRESS));
            multicastSocket.close();
        } catch (IOException e) {
            System.err.println("Error closing multicast socket: " + e.getMessage());
        }

        if (messageReceiverLoop != null && messageReceiverLoop.isAlive()) {
            messageReceiverLoop.interrupt(); // Прерываем поток
            try {
                messageReceiverLoop.join(); // Дожидаемся завершения
            } catch (InterruptedException e) {
                System.err.println("[ERROR]: Error while stopping listener: " + e.getMessage());
            }
        }

        if (socket != null && !socket.isClosed()) {
            socket.close(); // Закрываем сокет
        }
    }
}