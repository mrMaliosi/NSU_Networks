package ru.nsu.ccfit.malinovskii.Controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.Model.Context.NetworkContext;
import ru.nsu.ccfit.malinovskii.Model.Context.PlayerContext;
import ru.nsu.ccfit.malinovskii.Model.Context.ThreadContext;
import ru.nsu.ccfit.malinovskii.Model.GameLoop;
import ru.nsu.ccfit.malinovskii.Model.Object.Cell;
import ru.nsu.ccfit.malinovskii.Model.Object.CellType;
import ru.nsu.ccfit.malinovskii.Model.Object.Player;
import ru.nsu.ccfit.malinovskii.Model.Object.Snake;
import ru.nsu.ccfit.malinovskii.Thread.Listener;
import ru.nsu.ccfit.malinovskii.Thread.MasterSender;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.UnknownHostException;
import java.net.http.WebSocket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.net.InetAddress;
import java.util.function.Consumer;

import static java.lang.Thread.sleep;
import static ru.nsu.ccfit.malinovskii.Model.Context.GameContext.getContext;

public class GameController {
    @FXML
    private AnchorPane gamePane; // Корневой элемент из FXML
    @FXML
    private Canvas gameCanvas;
    @FXML
    public Label adminLabel;
    @FXML
    public Label sizeLabel;
    @FXML
    public Label foodLabel;
    @FXML
    public Label gameLabel;

    @FXML
    public TableView<Player> scoreTable;
    @FXML
    public TableColumn<Player, String> playerColumn;
    @FXML
    public TableColumn<Player, Integer> pointsColumn;
    @FXML
    public TableColumn<Player, String> colorColumn;

    private ObservableList<Player> playerList;

    private static final double START_X = 40; // Начальная X-координата области
    private static final double START_Y = 40; // Начальная Y-координата области
    private static final double END_X = 1150; // Конечная X-координата области
    private static final double END_Y = 760;  // Конечная Y-координата области

    private int gridWidth;
    private int gridHeight;

    static Random random = new Random();

    GameContext gameContext;
    PlayerContext playerContext;
    ThreadContext threadContext;

    Listener listener;

    MasterSender masterSender;

    GameLoop gameLoop;

    Snake currentSnake;

    private final ScheduledExecutorService gameScheduler = Executors.newSingleThreadScheduledExecutor();

    NetworkContext networkContext;

    public void initialize(int width, int height) {
        this.gridWidth = width;
        this.gridHeight = height;
        gameContext = getContext();
        playerContext = PlayerContext.getPlayerContext();
        gameLabel.setText(gameContext.getGameName());
        adminLabel.setText(playerContext.getPlayerName());
        sizeLabel.setText(String.valueOf(gameContext.getWidth()) + "x" + String.valueOf(gameContext.getHeight()));
        foodLabel.setText(String.valueOf(gameContext.getFoodCount()) + " + 1x");
        this.threadContext = ThreadContext.getContext();
        this.listener = threadContext.getListener();
        networkContext = NetworkContext.getContext();

        if (playerContext.getPlayerRole() != SnakesProto.NodeRole.MASTER){
            listener.addGameStateHandler(this::handleStateMsg);

            playerColumn.setCellValueFactory(new PropertyValueFactory<>("playerName"));
            pointsColumn.setCellValueFactory(new PropertyValueFactory<>("points"));


            // Привязываем свойство цвета к bodyColor из Snake
            colorColumn.setCellValueFactory(cellData -> {
                return new SimpleStringProperty(String.valueOf(cellData.getValue().getSnake().getBodyColor()));
            });

            // Привязываем кастомный рендерер для colorColumn
            colorColumn.setCellFactory(param -> new TableCell<Player, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        // Окрашиваем ячейку в цвет, полученный из Snake
                        // Убираем префикс '0x' и добавляем '#'
                        if (item.startsWith("0x")) {
                            item = "#" + item.substring(2);
                        }
                        setStyle("-fx-background-color: " + item + ";");
                        setText(null); // Убираем текст, оставляем только цвет
                    }
                }
            });

            // Устанавливаем сортировку по умолчанию для pointsColumn
            pointsColumn.setSortType(TableColumn.SortType.DESCENDING);
            scoreTable.getSortOrder().add(pointsColumn);
            scoreTable.sort();

            playerList = gameContext.getPlayerList();
            scoreTable.setItems(playerList);
            updateScoreTable(playerList);

            draw();
            setupKeyListeners();
            Platform.runLater(() -> gamePane.requestFocus());
            startListenerThread();
        } else {
            spawnSnake(playerContext.getPlayerName());
            this.currentSnake = playerContext.getSnake();
            for (int i = 0; i < gameContext.getFoodCount() + GameContext.getContext().getPlayerNumber(); ++i){
                spawnApple();
            }
            this.masterSender = new MasterSender();
            try {
                this.masterSender.startAsServer();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            playerColumn.setCellValueFactory(new PropertyValueFactory<>("playerName"));
            pointsColumn.setCellValueFactory(new PropertyValueFactory<>("points"));


            // Привязываем свойство цвета к bodyColor из Snake
            colorColumn.setCellValueFactory(cellData -> {
                return new SimpleStringProperty(String.valueOf(cellData.getValue().getSnake().getBodyColor()));
            });

            // Привязываем кастомный рендерер для colorColumn
            colorColumn.setCellFactory(param -> new TableCell<Player, String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        // Окрашиваем ячейку в цвет, полученный из Snake
                        // Убираем префикс '0x' и добавляем '#'
                        if (item.startsWith("0x")) {
                            item = "#" + item.substring(2);
                        }
                        setStyle("-fx-background-color: " + item + ";");
                        setText(null); // Убираем текст, оставляем только цвет
                    }
                }
            });

            // Устанавливаем сортировку по умолчанию для pointsColumn
            pointsColumn.setSortType(TableColumn.SortType.DESCENDING);
            scoreTable.getSortOrder().add(pointsColumn);
            scoreTable.sort();

            playerList = gameContext.getPlayerList();
            scoreTable.setItems(playerList);
            updateScoreTable(playerList);

            draw();
            this.gameLoop = new GameLoop();
            setupKeyListeners();
            Platform.runLater(() -> gamePane.requestFocus());
            // Обработчик закрытия окна
            Platform.runLater(() -> {
                Stage stage = (Stage) gamePane.getScene().getWindow();
                stage.setOnCloseRequest(event -> {
                    stopGameLoop();
                    masterSender.stopServer();
                    gameContext.deleteGameContext();
                });
            });

            startGameLoop();
        }
    }

    private void drawGrid() {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();

        // Вычисляем размеры области
        double areaWidth = END_X - START_X;
        double areaHeight = END_Y - START_Y;

        // Определяем размер клетки (они должны быть квадратными)
        double cellSize = Math.min(areaWidth / gridWidth, areaHeight / gridHeight);

        // Центрируем сетку в пределах области
        double offsetX = START_X + (areaWidth - cellSize * gridWidth) / 2;
        double offsetY = START_Y + (areaHeight - cellSize * gridHeight) / 2;

        // Рисуем фон
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(START_X, START_Y, areaWidth, areaHeight);

        // Рисуем клетки
        gc.setStroke(Color.BLACK);
        for (int i = 0; i <= gridWidth; i++) {
            double x = offsetX + i * cellSize;
            gc.strokeLine(x, offsetY, x, offsetY + gridHeight * cellSize);
        }

        for (int j = 0; j <= gridHeight; j++) {
            double y = offsetY + j * cellSize;
            gc.strokeLine(offsetX, y, offsetX + gridWidth * cellSize, y);
        }
    }

    private void draw() {
        GraphicsContext gc = gameCanvas.getGraphicsContext2D();

        // Вычисляем размеры области
        double areaWidth = END_X - START_X;
        double areaHeight = END_Y - START_Y;

        // Определяем размер клетки (они должны быть квадратными)
        double cellSize = Math.min(areaWidth / gridWidth, areaHeight / gridHeight);

        // Центрируем сетку в пределах области
        double offsetX = START_X + (areaWidth - cellSize * gridWidth) / 2;
        double offsetY = START_Y + (areaHeight - cellSize * gridHeight) / 2;

        for (int y = 0; y < gridHeight; ++y) {
            for (int x = 0; x < gridWidth; ++x) {
                Cell cell = gameContext.getCell(y, x);

                // Определяем цвет клетки в зависимости от её типа
                Color cellColor = cell.getCellColor();

                // Рисуем ячейку
                double cellX = offsetX + x * cellSize;
                double cellY = offsetY + y * cellSize;

                gc.setFill(cellColor);
                gc.fillRect(cellX, cellY, cellSize, cellSize);

                // Обводим контур клетки
                gc.setStroke(Color.BLACK);
                gc.strokeRect(cellX, cellY, cellSize, cellSize);
            }
        }
        updateScoreTable(playerList);
    }

    public void startGameLoop() {
        int tickDelay = gameContext.getTickDelay(); // Получаем задержку между тиками

        gameScheduler.scheduleAtFixedRate(() -> {
            try {
                gameLoop.gameThread(); // Выполнение игровой логики
                draw();
                masterSender.sendGrid();
            } catch (Exception e) {
                e.printStackTrace(); // Логируем ошибки, если они есть
            }
        }, tickDelay, tickDelay, TimeUnit.MILLISECONDS);
    }

    private void handleStateMsg(SnakesProto.GameMessage.StateMsg stateMsg) {
        if (stateMsg.hasState()) {
            gameContext.setEmptyGrid();
            gameContext.writeGame(stateMsg);
            playerContext.setSnake(gameContext.getLastSnake());
            this.currentSnake = playerContext.getSnake();
            updateScoreTable(playerList);
            draw();
        }
    }


    private void startListenerThread() {
        Thread listenerThread = new Thread(() -> listener.recieveClientMsgLoop());
        listenerThread.setDaemon(false); // Поток завершится вместе с приложением
        listenerThread.start();
    }

    public void stopGameLoop() {
        gameScheduler.shutdown(); // Останавливаем поток
        try {
            if (!gameScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                gameScheduler.shutdownNow(); // Принудительное завершение, если не завершился
            }
        } catch (InterruptedException e) {
            gameScheduler.shutdownNow(); // Прерывание при ожидании завершения
        }
    }


    public static Boolean spawnSnake(String playerName) {
        GameContext gameContext = GameContext.getContext();
        int gridWidth = gameContext.getWidth();
        int gridHeight = gameContext.getHeight();
        int x = random.nextInt(gridWidth - 5);
        int y = random.nextInt(gridHeight - 5);
        int tries = 0;
        int ch = 0;
        while (1 > 0 && tries < 100){
            for (int j = y; j < y + 5; ++j) {
                for (int i = x; i < x + 5; ++i) {
                    Cell cell = gameContext.getCell(j, i);
                    if (cell.getType() == CellType.SNAKE || cell.getType() == CellType.HEAD){
                        ch = 1;
                        break;
                    }
                }
                if (ch == 1) break;
            }
            ++tries;
        }

        int direction = random.nextInt(4) + 1;
        Snake snake = new Snake();
        switch(direction){
            case SnakesProto.Direction.UP_VALUE:
                snake.setDirection(SnakesProto.Direction.UP);
                if (y == 0){
                    Cell cell = new Cell(y, x, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y + 1, x, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                } else {
                    Cell cell = new Cell(y - 1, x, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y, x, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                }
                break;
            case SnakesProto.Direction.DOWN_VALUE:
                snake.setDirection(SnakesProto.Direction.DOWN);
                if (y == 0){
                    Cell cell = new Cell(y + 1, x, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y, x, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                } else {
                    Cell cell = new Cell(y, x, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y - 1, x, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                }
                break;
            case SnakesProto.Direction.LEFT_VALUE:
                snake.setDirection(SnakesProto.Direction.LEFT);
                if (x == 0){
                    Cell cell = new Cell(y, x, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y, x + 1, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                } else {
                    Cell cell = new Cell(y, x - 1, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y, x, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                }
                break;
            case SnakesProto.Direction.RIGHT_VALUE:
                snake.setDirection(SnakesProto.Direction.RIGHT);
                if (x == 0){
                    Cell cell = new Cell(y, x + 1, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y, x, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                } else {
                    Cell cell = new Cell(y, x, CellType.HEAD, snake.getHeadColor());
                    snake.addCell(cell);
                    cell = new Cell(y, x - 1, CellType.SNAKE, snake.getBodyColor());
                    snake.addCell(cell);
                }
                break;
        }

        PlayerContext playerContext = PlayerContext.getPlayerContext();
        playerContext.setSnake(snake);
        Player player = new Player(
                playerName,
                GameContext.colorToId(snake.getBodyColor()),
                playerContext.getPlayerRole(),
                playerContext.getPlayerType(),
                0,
                snake
        );

        gameContext.addPlayer(player);
        return true;
    }

    private void spawnApple() {
        gameContext.addApple();
    }

    private void setupKeyListeners() {
        gamePane.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case UP:
                    System.out.println("[LOG]: UP");
                    changeSnakeDirection(SnakesProto.Direction.UP);
                    break;
                case DOWN:
                    System.out.println("[LOG]: DOWN");
                    changeSnakeDirection(SnakesProto.Direction.DOWN);
                    break;
                case LEFT:
                    System.out.println("[LOG]: LEFT");
                    changeSnakeDirection(SnakesProto.Direction.LEFT);
                    break;
                case RIGHT:
                    System.out.println("[LOG]: RIGHT");
                    changeSnakeDirection(SnakesProto.Direction.RIGHT);
                    break;
                default:
                    break;
            }
        });
    }

    private void changeSnakeDirection(SnakesProto.Direction newDirection) {
        // Логика смены направления для змеи
        System.out.println(currentSnake);
        if (currentSnake != null && isDirectionChangeValid(currentSnake.getDirection(), newDirection)) {
            currentSnake.setNewDirection(newDirection);

            if (playerContext.getPlayerRole() != SnakesProto.NodeRole.MASTER){
                System.out.println("[LOG]: Sending steer message.");
                sendSteerMsg(newDirection, listener.getMasterAddress(), listener.getMasterPort());
            }
        }
    }

    private void sendSteerMsg(SnakesProto.Direction newDirection, InetAddress masterAddress, int masterPort) {
        try {
            // Создаём сообщение SteerMsg
            SnakesProto.GameMessage.SteerMsg steerMsg = SnakesProto.GameMessage.SteerMsg.newBuilder()
                    .setDirection(newDirection) // Устанавливаем направление
                    .build();

            // Оборачиваем SteerMsg в GameMessage
            SnakesProto.GameMessage gameMessage = SnakesProto.GameMessage.newBuilder()
                    .setSteer(steerMsg) // Вкладываем SteerMsg в сообщение
                    .setMsgSeq(networkContext.generateNextMsgSeq()) // Генерируем уникальный msgSeq
                    .build();

            // Кодируем сообщение в байты
            byte[] messageBytes = gameMessage.toByteArray();

            // Создаём UDP-пакет
            DatagramPacket packet = new DatagramPacket(
                    messageBytes,
                    messageBytes.length,
                    masterAddress,
                    masterPort
            );

            // Отправляем пакет через сокет
            networkContext.getSocket().send(packet);
            System.out.println("[INFO]: SteerMsg sent to master at " + masterAddress + ":" + masterPort);
        } catch (IOException e) {
            System.err.println("[ERROR]: Failed to send SteerMsg: " + e.getMessage());
        }
    }

    // Проверка, можно ли сменить направление (нельзя двигаться в противоположную сторону)
    private boolean isDirectionChangeValid(SnakesProto.Direction currentDirection, SnakesProto.Direction newDirection) {
        if (currentDirection == SnakesProto.Direction.UP && newDirection == SnakesProto.Direction.DOWN) return false;
        if (currentDirection == SnakesProto.Direction.DOWN && newDirection == SnakesProto.Direction.UP) return false;
        if (currentDirection == SnakesProto.Direction.LEFT && newDirection == SnakesProto.Direction.RIGHT) return false;
        if (currentDirection == SnakesProto.Direction.RIGHT && newDirection == SnakesProto.Direction.LEFT) return false;
        return true;
    }

    // Метод для обновления таблицы
    public void updateScoreTable(List<Player> players) {
        // Обновление данных
        // Принудительное обновление списка
        //playerList.clear();
        //playerList.addAll(players);

        // Уведомляем таблицу об изменениях
        scoreTable.refresh();

        Platform.runLater(() -> {
            // Сортировка по очкам (по убыванию)
            pointsColumn.setSortType(TableColumn.SortType.DESCENDING);
            scoreTable.getSortOrder().add(pointsColumn);
            scoreTable.sort();
        });
    }

}