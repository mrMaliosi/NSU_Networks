package ru.nsu.ccfit.malinovskii.Thread;

import ru.nsu.ccfit.malinovskii.Controller.GameController;
import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.Model.Context.NetworkContext;
import ru.nsu.ccfit.malinovskii.Model.Context.PlayerContext;
import ru.nsu.ccfit.malinovskii.Model.Object.Grid;
import ru.nsu.ccfit.malinovskii.Model.Object.Snake;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static ru.nsu.ccfit.malinovskii.Model.GameConfigBuilder.createGameConfig;

public class MasterSender {
    NetworkContext networkContext = NetworkContext.getContext();
    public static final String MULTICAST_ADDRESS = NetworkContext.getContext().getMulticastAddress();
    public static final int MULTICAST_PORT = NetworkContext.getContext().getMulticastPort();
    public static final long announcementDelayMS = NetworkContext.getContext().getAnnouncementDelayMS();
    private final ConcurrentHashMap<Integer, SnakesProto.GamePlayer> players = new ConcurrentHashMap();
    private final ConcurrentHashMap<InetSocketAddress, Integer> addressToPlayerId = new ConcurrentHashMap();
    private final Map<Integer, Long> lastPingTimeByNode = new ConcurrentHashMap<>(); // Для отслеживания пингов от нод
    //private final ConcurrentHashMap<Integer, ConcurrentHashMap<Long, MessageInfo>> sentMessages = new ConcurrentHashMap();
    private volatile InetAddress serverAddress;
    private volatile int serverPort;
    private DatagramSocket socket;
    private MulticastSocket multicastSocket;
    private Thread announcementSendThread;
    private Thread pingThread;
    private Thread deputyThread;
    private volatile int playerId = -1;
    private volatile int deputyId = -1;
    private volatile int masterId = -1;
    private volatile int currentMaxId = 0;
    private final AtomicLong msgSeq = new AtomicLong(0L);

    private int numberMsgSeq = 0;

    public GameContext gameContext = GameContext.getContext();
    public PlayerContext playerContext = PlayerContext.getPlayerContext();

    private Thread receiverThread;

    public void startAsServer() throws IOException {
        this.multicastSocket = new MulticastSocket(9192);
        this.serverAddress = InetAddress.getByName(NetworkContext.getAddress("eth0"));
        this.serverPort = 81;
        this.socket = new DatagramSocket(serverPort, serverAddress);
        //this.serverPort = socket.getLocalPort();
        int playerId = this.addNewPlayer(playerContext.getPlayerName(), this.serverAddress, this.serverPort, SnakesProto.NodeRole.MASTER, GameContext.colorToId(gameContext.getLastSnake().getBodyColor()));
        this.playerId = playerId;
        playerContext.setPlayerID(playerId);
        this.masterId = playerId;
        startServerThreads();
    }

    public ConcurrentHashMap<Integer, SnakesProto.GamePlayer> getPlayers(){
        return players;
    }

    public InetAddress getServerAddress(){
        return serverAddress;
    }

    public int getServerPort(){
        return serverPort;
    }

    private void startServerThreads() {
        //if (pingSender != null) pingSender.interrupt();

        announcementSendThread = new Thread(() -> {
            while (!announcementSendThread.isInterrupted()) {
                try {
                    SnakesProto.GameMessage announcement = createAnnouncementMessage();
                    sendGameMessage(announcement, InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
                    Thread.sleep(announcementDelayMS);
                } catch (InterruptedException | IOException e) {
                    System.err.println("[Server] Announcement send error!");
                    break;
                }
            }
        });

        receiverThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            // Хранение обработанных сообщений по MsgSeq
            Set<Long> processedMsgSeq = new HashSet<>();

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    socket.receive(packet);

                    byte[] data = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                    SnakesProto.GameMessage receivedMessage = SnakesProto.GameMessage.parseFrom(data);

                    // Получение MsgSeq
                    long msgSeq = receivedMessage.getMsgSeq();

                    // Проверяем, не было ли это сообщение уже обработано
                    //if (processedMsgSeq.contains(msgSeq)) {
                        //System.out.println("[INFO]: Duplicate message with MsgSeq " + msgSeq + " ignored.");
                        //continue;
                    //}

                    // Добавляем сообщение в обработанные
                    processedMsgSeq.add(msgSeq);

                    // Проверяем тип сообщения
                    switch (receivedMessage.getTypeCase()) {
                        case JOIN:
                            handleJoinMessage(receivedMessage, packet.getAddress(), packet.getPort());
                            break;
                        case STEER:
                            System.out.println("[INFO]: Steer message type.");
                            handleSteerMessage(receivedMessage, packet.getAddress(), packet.getPort());
                            break;
                        default:
                            //System.out.println("[INFO]: Unknown or unhandled message type.");
                            break;
                    }

                    // Отправляем AckMsg в ответ, если сообщение требует подтверждения
                    if (receivedMessage.hasMsgSeq()) {
                        sendAckMessage(msgSeq, packet.getAddress(), packet.getPort());
                    }
                } catch (IOException e) {
                    System.err.println("Receiver thread interrupted or error occurred: " + e.getMessage());
                    break;
                }
            }
        });

        // Поток для проверки наличия живого Deputy
        deputyThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Время, через которое проверяется Deputy
                    long deputyCheckDelay = (long) (0.8 * gameContext.getTickDelay());

                    if (deputyId != -1) {
                        Long lastPingTime = lastPingTimeByNode.get(deputyId);
                        long currentTime = System.currentTimeMillis();

                        // Проверяем, получен ли ping от Deputy в пределах допустимого времени
                        if (lastPingTime == null || (currentTime - lastPingTime) > deputyCheckDelay) {
                            System.out.println("[WARN]: Deputy not responding. Assigning a new deputy.");
                            assignNewDeputy();
                        }
                    } else {
                        assignNewDeputy();
                    }

                    Thread.sleep(deputyCheckDelay); // Задержка перед следующей проверкой
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[INFO]: Deputy thread interrupted.");
                } catch (Exception e) {
                    System.err.println("[ERROR]: Error in deputy thread: " + e.getMessage());
                }
            }
        });

        // Поток для отправки PingMessage
        pingThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Получаем список всех нод (игроков)
                    for (SnakesProto.GamePlayer player : players.values()) {
                        if (!player.getRole().equals(SnakesProto.NodeRole.MASTER)) { // Игнорируем себя
                            SnakesProto.GameMessage pingMessage = SnakesProto.GameMessage.newBuilder()
                                    .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                                    .setMsgSeq(++numberMsgSeq) // Уникальная последовательность
                                    .build();

                            sendGameMessage(pingMessage, InetAddress.getByName(player.getIpAddress()), player.getPort());
                        }
                    }
                    Thread.sleep(networkContext.getPingDelayMS()); // Задержка перед следующим пингом
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("[INFO]: Ping thread interrupted.");
                } catch (Exception e) {
                    System.err.println("[ERROR]: Error in ping thread: " + e.getMessage());
                }
            }
        });

        announcementSendThread.start();
        pingThread.start();
        deputyThread.start();
        receiverThread.start();
    }

    public void stopServer() {
        announcementSendThread.interrupt();
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
    }

    private void handleSteerMessage(SnakesProto.GameMessage receivedMessage, InetAddress address, int port) {
        // Проверяем, содержит ли сообщение SteerMsg
        if (!receivedMessage.hasSteer()) {
            System.out.println("[WARN]: Received message is not a SteerMsg.");
            return;
        }

        // Извлекаем SteerMsg
        SnakesProto.GameMessage.SteerMsg steerMsg = receivedMessage.getSteer();
        SnakesProto.Direction newDirection = steerMsg.getDirection();

        // Логируем полученное направление
        System.out.println("[INFO]: Received SteerMsg from " + address + ":" + port +
                " with direction " + newDirection);

        int senderPlayerId = -1;
        // Обновляем направление игрока в игровом контексте
        for (SnakesProto.GamePlayer player : players.values()) {
            try {
                if (player.getPort() == port && InetAddress.getByName(player.getIpAddress()) == address){
                    senderPlayerId = player.getId();
                    break;
                }
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        if (senderPlayerId == -1) {
            System.out.println("[WARN]: Unknown player tried to send SteerMsg from " + address + ":" + port);
            return;
        }

        // Передача нового направления в контекст игры
        if (gameContext != null) {
            boolean success = gameContext.updatePlayerDirection(senderPlayerId, newDirection);
            if (!success) {
                System.out.println("[WARN]: Failed to update direction for player ID " + senderPlayerId);
            } else {
                System.out.println("[INFO]: Player ID " + senderPlayerId + " direction updated to " + newDirection);
            }
        }

        // Отправляем AckMsg в ответ
        sendAckMessage(receivedMessage.getMsgSeq(), address, port);
    }

    private void sendAckMessage(long msgSeq, InetAddress address, int port) {
        try {
            // Создаем сообщение AckMsg
            SnakesProto.GameMessage.AckMsg ackMsg = SnakesProto.GameMessage.AckMsg.newBuilder().build();

            // Встраиваем AckMsg в GameMessage
            SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                    .setAck(ackMsg)
                    .setMsgSeq(msgSeq) // Устанавливаем тот же MsgSeq
                    .build();

            // Кодируем сообщение в байты
            byte[] data = message.toByteArray();

            // Отправляем сообщение через сокет
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);

            System.out.println("[INFO]: AckMsg sent for MsgSeq " + msgSeq + " to " + address + ":" + port);
        } catch (IOException e) {
            System.err.println("[ERROR]: Failed to send AckMsg: " + e.getMessage());
        }
    }

    // Метод для назначения нового Deputy
    private void assignNewDeputy() {
        for (SnakesProto.GamePlayer player : players.values()) {
            if (!player.getRole().equals(SnakesProto.NodeRole.MASTER)) { // Исключаем себя
                deputyId = player.getId();

                // Создаем сообщение RoleChangeMsg
                SnakesProto.GameMessage.RoleChangeMsg roleChangeMsg = SnakesProto.GameMessage.RoleChangeMsg.newBuilder()
                        .setSenderRole(SnakesProto.NodeRole.MASTER) // Текущая роль отправителя
                        .setReceiverRole(SnakesProto.NodeRole.DEPUTY) // Новая роль для получателя
                        .build();

                // Упаковываем сообщение в GameMessage
                SnakesProto.GameMessage roleChangeMessage = SnakesProto.GameMessage.newBuilder()
                        .setRoleChange(roleChangeMsg)
                        .setMsgSeq(++numberMsgSeq) // Генерация уникальной последовательности
                        .build();

                // Отправляем RoleChangeMsg новому Deputy
                try {
                    sendGameMessage(roleChangeMessage, InetAddress.getByName(player.getIpAddress()), player.getPort());
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }

                System.out.println("[INFO]: New Deputy assigned: " + deputyId + ". RoleChangeMsg sent.");
                return;
            }
        }
        System.out.println("[WARN]: No suitable player found to assign as Deputy.");
    }

    // Метод для обновления времени последнего PingMessage
    public void updatePingTime(int nodeId) {
        lastPingTimeByNode.put(nodeId, System.currentTimeMillis());
    }


    private void handleJoinMessage(SnakesProto.GameMessage receivedMessage, InetAddress address, int port) {
        System.out.println("[LOG]: get join message.");
        SnakesProto.GameMessage.JoinMsg joinMsg = receivedMessage.getJoin();

        GameController.spawnSnake(joinMsg.getPlayerName());
        Snake newSnake = gameContext.getLastSnake();

        // Добавляем нового игрока
        int newPlayerId = addNewPlayer(joinMsg.getPlayerName(), address, port, joinMsg.getRequestedRole(), GameContext.colorToId(newSnake.getBodyColor()));
        if (newPlayerId == -1) {
            System.err.println("Failed to add new player: " + joinMsg.getPlayerName());
            return;
        }

        System.out.println("New player joined: " + joinMsg.getPlayerName() + " (ID: " + newPlayerId + ")");

        // Отправляем AckMsg новому игроку
        SnakesProto.GameMessage.AckMsg ackMsg = SnakesProto.GameMessage.AckMsg.newBuilder()
                .build();

        SnakesProto.GameMessage ackMessage = SnakesProto.GameMessage.newBuilder()
                .setAck(ackMsg)
                .setMsgSeq(receivedMessage.getMsgSeq())
                .setReceiverId(newPlayerId)
                .setSenderId(this.playerId)
                .build();

        sendGameMessage(ackMessage, address, port);
    }

    public void sendGrid(){
        ConcurrentHashMap<Integer, SnakesProto.GamePlayer> updatedPlayers = new ConcurrentHashMap<>();

        for (SnakesProto.GamePlayer player : players.values()) {
            SnakesProto.GamePlayer updatedPlayer = SnakesProto.GamePlayer.newBuilder(player)
                    .setScore(gameContext.getScoreByPlayerID(player.getId()))
                    .build();
            updatedPlayers.put(updatedPlayer.getId(), updatedPlayer);
        }

        players.clear();
        players.putAll(updatedPlayers);

        // Создание игрового состояния
        SnakesProto.GameState.Builder gameStateBuilder = SnakesProto.GameState.newBuilder()
                .setStateOrder(gameContext.getStateOrder())
                .addAllSnakes(gameContext.getSnakes())
                .addAllFoods(gameContext.getFood())
                .setPlayers(SnakesProto.GamePlayers.newBuilder()
                        .addAllPlayers(players.values())
                        .build());

        SnakesProto.GameState gameState = gameStateBuilder.build();

        // Отправка StateMsg каждому игроку
        for (SnakesProto.GamePlayer player : players.values()) {
            try {
                InetAddress playerAddress = InetAddress.getByName(player.getIpAddress());
                int playerPort = player.getPort();
                if (playerAddress != serverAddress && playerPort != serverPort){
                    sendStateMsg(gameState, playerAddress, playerPort);
                }
            } catch (UnknownHostException e) {
                System.err.println("Error resolving IP address for player: " + player.getName());
            }
        }
    }



    private void sendAnnouncmentsThread() {
        while (!Thread.currentThread().isInterrupted()) {
            if (socket == null) continue;
            try {
                byte[] buffer = new byte[4096];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private SnakesProto.GameMessage createAnnouncementMessage() {
        synchronized(gameContext.lockGameContext) {
            this.gameContext = GameContext.getContext();
        }

        String serverName = (String)this.gameContext.getGameName();
        return SnakesProto.GameMessage.newBuilder().setMsgSeq(this.msgSeq.incrementAndGet()).setAnnouncement(
                SnakesProto.GameMessage.AnnouncementMsg.newBuilder().addGames(
                        SnakesProto.GameAnnouncement.newBuilder().setPlayers(
                                SnakesProto.GamePlayers.newBuilder().addAllPlayers(this.players.values()).build())
                                .setConfig(createGameConfig(gameContext))
                                .setCanJoin(true).
                                setGameName(serverName)
                                .build())
                        .build())
                .build();
    }

    private SnakesProto.GameMessage createPingMessage() {
        return SnakesProto.GameMessage.newBuilder().setMsgSeq(this.msgSeq.incrementAndGet())
                .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
                .build();
    }

    private void sendGameMessage(SnakesProto.GameMessage gameMessage, InetAddress address, int port) {
        SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder(gameMessage).
                setSenderId(this.playerId).setReceiverId(this.getPlayerIdByAddress(address, port)).build();

        int player;
        try {
            byte[] buffer = message.toByteArray();
            DatagramPacket data = new DatagramPacket(buffer, buffer.length, address, port);
            PrintStream var10000;
            String var10001;
            if (this.players.get(message.getReceiverId()) != null) {
                var10000 = System.out;
                var10001 = String.valueOf(message.getTypeCase());
                var10000.println("Send message " + var10001 + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") to " + String.valueOf(address) + ":" + port + "(" + String.valueOf(((SnakesProto.GamePlayer)this.players.get(message.getReceiverId())).getRole()) + ")");
            } else {
                var10000 = System.out;
                var10001 = String.valueOf(message.getTypeCase());
                var10000.println("Send message " + var10001 + "(rec: " + message.getReceiverId() + ", sen: " + message.getSenderId() + ", seq: " + message.getMsgSeq() + ") to " + String.valueOf(address) + ":" + port);
            }

            if (message.getTypeCase() == SnakesProto.GameMessage.TypeCase.ANNOUNCEMENT) {
                this.multicastSocket.send(data);
            } else {
                this.socket.send(data);
                if (message.getReceiverId() == -1 || message.getTypeCase() == SnakesProto.GameMessage.TypeCase.ACK) {
                    return;
                }
            }
        } catch (IOException var7) {
            IOException e = var7;
            System.err.println("Error sending message: " + e.getMessage());
            player = this.getPlayerIdByAddress(address, port);
            //this.handlePlayerDisconnection(player);
        } catch (Exception var8) {
            player = message.getReceiverId();
            this.players.remove(player);
        }

    }

    private int addNewPlayer(String playerName, InetAddress address, int port, SnakesProto.NodeRole requestedRole, int playerID) {
        int playerId = playerID;
        System.out.println("ROLE " + String.valueOf(requestedRole));
        if (requestedRole == SnakesProto.NodeRole.MASTER && this.playerContext.getPlayerRole() != SnakesProto.NodeRole.MASTER) {
            return -1;
        } else {
            if (requestedRole == SnakesProto.NodeRole.DEPUTY) {
                if (this.deputyId != -1) {
                    return -1;
                }

                this.deputyId = playerId;
            }

            SnakesProto.GamePlayer player = SnakesProto.GamePlayer.newBuilder().setId(playerId).setName(playerName).setRole(requestedRole).setIpAddress(address.getHostAddress()).setPort(port).setScore(0).build();
            this.players.put(playerId, player);
            this.addressToPlayerId.put(new InetSocketAddress(address, port), playerId);
            //this.sentMessages.put(playerId, new ConcurrentHashMap());
            return playerId;
        }
    }

    private int getPlayerIdByAddress(InetAddress address, int port) {
        return (Integer)this.addressToPlayerId.getOrDefault(new InetSocketAddress(address, port), -1);
    }

    // Отправка сообщения StateMsg
    public void sendStateMsg(SnakesProto.GameState gameState, InetAddress address, int port) {
        System.out.println("[LOG]: Sent StateMsg.");
        SnakesProto.GameMessage.StateMsg stateMsg = SnakesProto.GameMessage.StateMsg.newBuilder()
                .setState(gameState)
                .build();

        SnakesProto.GameMessage stateMessage = SnakesProto.GameMessage.newBuilder()
                .setState(stateMsg)
                .setMsgSeq(msgSeq.incrementAndGet())
                .setSenderId(playerId)
                .build();

        sendGameMessage(stateMessage, address, port);
    }

    // Отправка сообщения AckMsg
    public void sendAckMsg(long msgSeq, int receiverId, InetAddress address, int port) {
        SnakesProto.GameMessage.AckMsg ackMsg = SnakesProto.GameMessage.AckMsg.newBuilder().build();

        SnakesProto.GameMessage ackMessage = SnakesProto.GameMessage.newBuilder()
                .setAck(ackMsg)
                .setMsgSeq(msgSeq)
                .setSenderId(playerId)
                .setReceiverId(receiverId)
                .build();

        sendGameMessage(ackMessage, address, port);
    }

    // Отправка сообщения ErrorMsg
    public void sendErrorMsg(String errorMessage, InetAddress address, int port) {
        SnakesProto.GameMessage.ErrorMsg errorMsg = SnakesProto.GameMessage.ErrorMsg.newBuilder()
                .setErrorMessage(errorMessage)
                .build();

        SnakesProto.GameMessage errorGameMessage = SnakesProto.GameMessage.newBuilder()
                .setError(errorMsg)
                .setMsgSeq(msgSeq.incrementAndGet())
                .setSenderId(playerId)
                .build();

        sendGameMessage(errorGameMessage, address, port);
    }
}

/*

// Поток для отправки сообщений
        announcementSendThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    SnakesProto.GameMessage.AnnouncementMsg announcementMsg = SnakesProto.GameMessage.AnnouncementMsg.newBuilder()
                            .setConfig(createGameConfig(gameContext))
                            .setCanJoin(true)
                            .build();

                    SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                            .setMsgSeq(msgSeq.incrementAndGet())
                            .setAnnouncement(announcementMsg)
                            .build();

                    byte[] data = message.toByteArray();
                    DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
                    multicastSocket.send(packet);

                    Thread.sleep(ANNOUNCEMENT_DELAY_MS);
                } catch (IOException | InterruptedException e) {
                    if (Thread.currentThread().isInterrupted()) break;
                    e.printStackTrace();
                }
            }
        });
        announcementSendThread.start();

public void startAsClient(String playerName, InetAddress serverAddress, int serverPort) throws IOException {
    this.nodeRole = SnakesProto.NodeRole.NORMAL;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
    this.socket = new DatagramSocket();

    sendJoinRequest(playerName, serverAddress, serverPort);

    pingSender = new Thread(() -> {
        while (!pingSender.isInterrupted()) {
            try {
                Thread.sleep(pingDelayMS);
                if (masterId == -1) continue;
                if (sentMessages.get(masterId).isEmpty()) {
                    SnakesProto.GameMessage pingMsg = createPingMessage();
                    sendGameMessage(pingMsg, serverAddress, serverPort);
                }
            } catch (InterruptedException e) {
                System.err.println("Ping sender error...");
                break;
            } catch (Exception e) {
                //ignore because no element in map
            }
        }
    });
    pingSender.start();
}

private void startServerThreads() {
    if (pingSender != null) pingSender.interrupt();

    announcementSendThread = new Thread(() -> {
        while (!announcementSendThread.isInterrupted()) {
            try {
                SnakesProto.GameMessage announcement = createAnnouncementMessage();
                sendGameMessage(announcement, InetAddress.getByName(MULTICAST_ADDRESS), MULTICAST_PORT);
                Thread.sleep(announcementDelayMS);
            } catch (InterruptedException | IOException e) {
                System.err.println("[Server] Announcement send error!");
                break;
            }
        }
    });

    gameLoop = new Thread(() -> {
        while (!gameLoop.isInterrupted()) {
            try {
                int delay;
                synchronized (lockSnakeGame) {
                    delay = snakeGame.getGameField().getDelayMS();
                }
                Thread.sleep(delay);
                synchronized (lockSnakeGame) {
                    snakeGame.update();
                }
                updatePlayersScore();
                sendStateForAll();
            } catch (InterruptedException | IOException e) {
                System.err.println("[Server] Game loop destroyed...");
                break;
            }
        }
    });

    announcementSendThread.start();
    gameLoop.start();
}

private SnakesProto.GameMessage createPingMessage() {
    return SnakesProto.GameMessage.newBuilder()
            .setMsgSeq(msgSeq.incrementAndGet())
            .setPing(SnakesProto.GameMessage.PingMsg.newBuilder().build())
            .build();
}
 */