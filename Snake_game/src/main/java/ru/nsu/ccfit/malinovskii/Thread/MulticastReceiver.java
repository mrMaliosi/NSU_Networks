package ru.nsu.ccfit.malinovskii.Thread;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

public class MulticastReceiver implements Runnable {
    private static final String MULTICAST_ADDRESS = "239.192.0.4";
    private static final int MULTICAST_PORT = 9192;
    private final MulticastSocket socket;

    public MulticastReceiver() throws IOException {
        this.socket = new MulticastSocket(MULTICAST_PORT);
        InetAddress group = InetAddress.getByName(MULTICAST_ADDRESS);
        socket.joinGroup(group);
    }

    @Override
    public void run() {
        byte[] buffer = new byte[2048]; // Увеличен размер для больших сообщений
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Декодируем сообщение с использованием protobuf
                SnakesProto.GameMessage message = SnakesProto.GameMessage.parseFrom(packet.getData());
                handleMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleMessage(SnakesProto.GameMessage message) {
        if (message.hasAnnouncement()) {
            System.out.println("Received AnnouncementMsg");
            SnakesProto.GameMessage.AnnouncementMsg announcement = message.getAnnouncement();
            announcement.getGamesList().forEach(game -> {
                System.out.println("Game: " + game.getGameName() + ", Players: " + game.getPlayers().getPlayersCount());
            });
        } else if (message.hasDiscover()) {
            System.out.println("Received DiscoverMsg");
        }
        // Добавьте обработку других типов сообщений при необходимости
    }
}