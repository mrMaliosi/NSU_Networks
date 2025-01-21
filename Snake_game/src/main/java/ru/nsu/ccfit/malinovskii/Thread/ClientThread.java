package ru.nsu.ccfit.malinovskii.Thread;

import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.Model.Context.NetworkContext;
import ru.nsu.ccfit.malinovskii.Model.Context.PlayerContext;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static ru.nsu.ccfit.malinovskii.Model.GameConfigBuilder.createGameConfig;

public class ClientThread {
    NetworkContext networkContext = NetworkContext.getContext();
    public static final String MULTICAST_ADDRESS = NetworkContext.getContext().getMulticastAddress();
    public static final int MULTICAST_PORT = NetworkContext.getContext().getMulticastPort();
    public static final long ANNOUNCEMENT_DELAY_MS = NetworkContext.getContext().getAnnouncementDelayMS();
    private final ConcurrentHashMap<Integer, SnakesProto.GamePlayer> players = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap<>();
    private volatile InetAddress serverAddress;
    private volatile int serverPort;
    private DatagramSocket socket;
    private MulticastSocket multicastSocket;
    private Thread announcementSendThread;
    private volatile int playerId = -1;
    private volatile int deputyId = -1;
    private volatile int masterId = -1;
    private final AtomicLong msgSeq = new AtomicLong(0L);

    public GameContext gameContext = GameContext.getContext();
    public PlayerContext playerContext = PlayerContext.getPlayerContext();

    public void startAsClient() throws IOException {
        this.multicastSocket = new MulticastSocket(MULTICAST_PORT);
        this.serverAddress = InetAddress.getByName(NetworkContext.getAddress("eth0"));
        this.serverPort = 81;
        this.socket = new DatagramSocket(serverPort, serverAddress);

        this.playerId = playerContext.getPlayerID();
        this.masterId = playerId;
        startClientThreads();
    }

    private void startClientThreads() {

    }

    // Метод для подключения к игре
    public void joinGame(SnakesProto.GameAnnouncement game) {
        String leaderAddress = null; // Адрес ведущего
        int leaderPort = -1; // Порт ведущего

        for (SnakesProto.GamePlayer gamePlayer : game.getPlayers().getPlayersList()) {
            if (gamePlayer.getRole() == SnakesProto.NodeRole.MASTER) {
                leaderAddress = gamePlayer.getIpAddress();
                leaderPort = gamePlayer.getPort();
                break;
            }
        }

        if (leaderAddress == null || leaderPort == -1) {
            System.err.println("Failed to find Master Node in the game announcement.");
            return;
        }

        System.out.println("Joining game: " + game.getGameName());
        System.out.println("Leader Address: " + leaderAddress + ", Port: " + leaderPort);

        // Логика подключения
        try {
            InetAddress leaderInetAddress = InetAddress.getByName(leaderAddress);
            DatagramSocket socket = new DatagramSocket();

            // Создание сообщения для отправки
            SnakesProto.GameMessage.JoinMsg joinMsg = SnakesProto.GameMessage.JoinMsg.newBuilder()
                    .setPlayerType(SnakesProto.PlayerType.HUMAN)
                    .setRequestedRole(SnakesProto.NodeRole.NORMAL)
                    .build();

            SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                    .setMsgSeq(msgSeq.incrementAndGet())
                    .setJoin(joinMsg)
                    .build();

            byte[] data = message.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, leaderInetAddress, leaderPort);

            socket.send(packet);
            System.out.println("Join request sent to " + leaderAddress + ":" + leaderPort);

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopClientThreads() {
        if (announcementSendThread != null && announcementSendThread.isAlive()) {
            announcementSendThread.interrupt();
        }
        if (multicastSocket != null && !multicastSocket.isClosed()) {
            try {
                multicastSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}