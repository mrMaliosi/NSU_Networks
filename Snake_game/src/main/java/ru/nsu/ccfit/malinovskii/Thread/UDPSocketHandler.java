package ru.nsu.ccfit.malinovskii.Thread;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;

import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

public class UDPSocketHandler {
    private final DatagramSocket socket;

    public UDPSocketHandler() throws IOException {
        this.socket = new DatagramSocket(); // ОС назначит порт автоматически
    }

    public void sendMessage(SnakesProto.GameMessage message, InetAddress address, int port) throws IOException {
        byte[] data = message.toByteArray();
        DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
        socket.send(packet);
    }
/*
    private void receiveMessageLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            if (socket == null) continue;
            try {
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] data = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(data);
                if (players.get(message.getSenderId()) != null) {
                    System.out.println("listened " + message.getTypeCase() + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") from " + address + ":" + port + "(" + players.get(message.getSenderId()).getRole() + ")");
                } else {
                    System.out.println("listened " + message.getTypeCase() + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") from " + address + ":" + port);
                }
                System.out.println("Listen: curr time = " + System.currentTimeMillis());

                if (message.getTypeCase() != SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT && message.getTypeCase() != SnakesProto.GameMessage.TypeCase.ACK && message.getTypeCase() != SnakesProto.GameMessage.TypeCase.JOIN) {
                    sendAck(message.getMsgSeq(), address, port);
                } else if (message.getTypeCase() == SnakesProto.GameMessage.TypeCase.ACK) {
                    handleAck(message, address, port);
                    continue;
                }

                int playerId = message.getSenderId();
                long playerMsgSeq = message.getMsgSeq();

                if (playerMsgSeq > lastMsgSeqReceived.getOrDefault(playerId, -1L)) {
                    if (playerId != -1) lastMsgSeqReceived.put(playerId, playerMsgSeq);
                    switch (message.getTypeCase()) {
                        case PING  -> handlePing(message, address, port);
                        case STEER -> handleSteer(message, address, port);
                        case JOIN  -> handleJoin(message, address, port);
                        case ANNOUNCEMENT -> handleAnnouncement(message, address, port);
                        case STATE -> handleState(message, address, port);
                        case ACK   -> handleAck(message, address, port);
                        case ERROR -> handleError(message, address, port);
                        case ROLE_CHANGE -> handleRoleChange(message, address, port);
                        default    -> {
                            System.err.println("Unknown message type (" + message.getTypeCase() + ") from " + address.toString() + port);
                            sendError("Unknown message type", address, port);
                            return;
                        }
                    }
                }

                System.out.println("AAAAAAAAAAAAAAAAAAAAAAAAAAA " + nodeRole + deputyId);
                if (nodeRole == SnakesProto.NodeRole.MASTER && deputyId == -1) {
                    System.out.println("FIND AAAAAAAAAAAAAAAAAAAAAAAAAAA");
                    selectNewDeputy();
                }
            } catch (IOException e) {
                System.err.println("Message receive error: " + e.getMessage());
                break;
            }
        }
    }
    */

    public void receiveMessages() {
        byte[] buffer = new byte[2048];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Декодируем сообщение
                SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(packet.getData());
                handleMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(SnakesProto.GameMessage message) {
        if (message.hasPing()) {
            System.out.println("Received PingMsg");
        } else if (message.hasState()) {
            System.out.println("Received StateMsg");
        }
        // Обрабатывайте остальные сообщения при необходимости
    }
}
