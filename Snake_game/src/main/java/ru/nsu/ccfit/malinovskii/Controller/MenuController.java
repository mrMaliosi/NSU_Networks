package ru.nsu.ccfit.malinovskii.Controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.Model.Context.NetworkContext;
import ru.nsu.ccfit.malinovskii.Model.Context.ThreadContext;
import ru.nsu.ccfit.malinovskii.Model.Message.GameInfo;
import ru.nsu.ccfit.malinovskii.Thread.Listener;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ru.nsu.ccfit.malinovskii.Model.Context.GameContext.deleteGameContext;
import static ru.nsu.ccfit.malinovskii.Model.Context.GameContext.getContext;
import static ru.nsu.ccfit.malinovskii.Model.Context.NetworkContext.deleteNetworkContext;
import static ru.nsu.ccfit.malinovskii.Model.Context.PlayerContext.deletePlayerContext;

public class MenuController {
    @FXML
    public Button exitButton;
    @FXML
    public Button newGameButton;
    @FXML
    public ListView listOfGames;

    private Stage newGameStage = null; // Переменная для хранения текущего окна новой игры
    private Stage joinGameStage = null; // Переменная для хранения текущего окна присоединения к игре

    @FXML
    public TableView<GameInfo> gamesTable;
    @FXML
    public TableColumn<GameInfo, String> gameNameColumn;
    @FXML
    public TableColumn<GameInfo, String> leaderColumn;
    @FXML
    public TableColumn<GameInfo, Integer> playersCountColumn;
    @FXML
    public TableColumn<GameInfo, String> configColumn;
    @FXML
    public TableColumn<GameInfo, Button> joinButtonColumn;

    private ObservableList<GameInfo> gamesList = FXCollections.observableArrayList();

    ThreadContext threadContext;
    Listener listener;

    private final Map<String, Long> gameTimestamps = new HashMap<>(); // Хранение времени жизни игр
    private static final long GAME_LIFETIME = 5000; // Время жизни игры в миллисекундах (5 секунд)
    private static final long CLEANUP_INTERVAL = 2000; // Интервал проверки устаревших игр (2 секунды)
    private ScheduledExecutorService cleanupExecutor; // Планировщик для проверки

    @FXML
    public void initialize() {
        gameNameColumn.setCellValueFactory(new PropertyValueFactory<>("gameName"));
        leaderColumn.setCellValueFactory(new PropertyValueFactory<>("leader"));
        playersCountColumn.setCellValueFactory(new PropertyValueFactory<>("playersCount"));
        configColumn.setCellValueFactory(new PropertyValueFactory<>("config"));
        joinButtonColumn.setCellValueFactory(new PropertyValueFactory<>("joinButton"));
        // Создаем кастомную ячейку для кнопки "Присоединиться"
        joinButtonColumn.setCellFactory(param -> new TableCell<GameInfo, Button>() {
            @Override
            protected void updateItem(Button item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null); // Если данных нет, ячейка остается пустой
                } else {
                    setGraphic(item); // Устанавливаем кнопку
                }
            }
        });

        // Привязка данных к таблице
        gamesTable.setItems(gamesList);

        this.threadContext = ThreadContext.getContext();
        this.listener = new Listener(this::addGameToTable);
        threadContext.addListener(this.listener);
        threadContext.getListener().start();
        startCleanupThread();

        newGameButton.setOnAction(e -> {
            threadContext.getListener().start();
            try {
                // Если окно новой игры уже открыто, закрываем его
                if (newGameStage != null && newGameStage.isShowing()) {
                    newGameStage.close();
                    deleteNetworkContext();
                    deletePlayerContext();
                    deleteGameContext();
                }

                // Загружаем FXML для нового окна мотивации
                URL motivationXmlUrl = getClass().getResource("/ru/nsu/ccfit/malinovskii/view/newGame-view.fxml");
                FXMLLoader loader = new FXMLLoader(motivationXmlUrl);

                // Загружаем сцену для нового окна
                Scene scene = new Scene(loader.load());

                // Получаем доступ к контроллеру мотивации
                NewGameController controller = loader.getController();

                // Создаем новый Stage для окна мотивации
                newGameStage = new Stage();
                newGameStage.setTitle("Create New Game!"); // Заголовок окна
                newGameStage.setScene(scene);
                newGameStage.show(); // Показываем новое окно

            } catch (IOException ioException) {
                ioException.printStackTrace(); // Логируем ошибку, если не удается загрузить FXML
            }
        });

        exitButton.setOnAction(e -> {
            stopCleanupThread();
            Platform.exit();
        });
    }

    public void addGameToTable(SnakesProto.GameMessage.AnnouncementMsg gameAnnouncement) {
        long currentTime = System.currentTimeMillis(); // Текущее время

        Platform.runLater(() -> {
            // Удаляем устаревшие игры
            gameTimestamps.entrySet().removeIf(entry -> currentTime - entry.getValue() > GAME_LIFETIME);

            // Обновляем список игр
            gameAnnouncement.getGamesList().forEach(game -> {
                String gameName = game.getGameName();

                // Если игра уже есть в списке, обновляем её время
                if (gameTimestamps.containsKey(gameName)) {
                    gameTimestamps.put(gameName, currentTime);
                } else {
                    // Создаем кнопку для присоединения
                    Button joinButton = new Button("Присоединиться");

                    joinButton.setOnAction(e -> {            //joinGame(game));'
                        try {
                            // Если окно новой игры уже открыто, закрываем его
                            if (joinGameStage != null && joinGameStage.isShowing()) {
                                joinGameStage.close();
                                deleteNetworkContext();
                                deletePlayerContext();
                                deleteGameContext();
                            }

                            GameContext gameContext = GameContext.getContext(
                                    gameName,
                                    game.getConfig().getWidth(),
                                    game.getConfig().getHeight(),
                                    game.getConfig().getFoodStatic(),
                                    game.getConfig().getStateDelayMs()
                            );


                            // Загружаем FXML для нового окна мотивации
                            URL joinXmlUrl = getClass().getResource("/ru/nsu/ccfit/malinovskii/view/join-view.fxml");
                            FXMLLoader loader = new FXMLLoader(joinXmlUrl);

                            // Загружаем сцену для нового окна
                            Scene scene = new Scene(loader.load());

                            // Получаем доступ к контроллеру присоединения
                            JoinGameController controller = loader.getController();

                            // Передаем gameAnnouncement в контроллер
                            controller.setGameAnnouncement(game);

                            // Создаем новый Stage для окна мотивации
                            newGameStage = new Stage();
                            newGameStage.setTitle("Присоединиться к игре: " + game.getGameName()); // Заголовок окна
                            newGameStage.setScene(scene);
                            newGameStage.show(); // Показываем новое окно
                        } catch (IOException ioException) {
                            ioException.printStackTrace(); // Логируем ошибку, если не удается загрузить FXML
                        }
                    });


                    String leaderName = game.getPlayers().getPlayers(0).getName(); // Имя ведущего игрока
                    int playerCount = game.getPlayers().getPlayersCount(); // Количество игроков
                    String configDetails = game.getConfig().getWidth() + "x" +
                            game.getConfig().getHeight() +
                            ", Food: " + game.getConfig().getFoodStatic(); // Размер поля и информация о еде

                    GameInfo gameInfo = new GameInfo(
                            gameName,          // Название игры
                            leaderName,         // Ведущий игрок
                            playerCount,        // Количество игроков
                            configDetails,      // Конфигурация игры
                            joinButton          // Кнопка "Присоединиться"
                    );

                    // Добавляем игру в список и обновляем её время
                    gamesList.add(gameInfo);
                    gameTimestamps.put(gameName, currentTime);
                }
            });
        });
    }

    private void startCleanupThread() {
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

        cleanupExecutor.scheduleAtFixedRate(() -> {
            long currentTime = System.currentTimeMillis();

            Platform.runLater(() -> {
                gameTimestamps.entrySet().removeIf(entry -> {
                    if (currentTime - entry.getValue() > GAME_LIFETIME) {
                        gamesList.removeIf(gameInfo -> gameInfo.getGameName().equals(entry.getKey()));
                        return true; // Удаляем запись из gameTimestamps
                    }
                    return false;
                });
            });
        }, 0, CLEANUP_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public void stopCleanupThread() {
        if (cleanupExecutor != null && !cleanupExecutor.isShutdown()) {
            cleanupExecutor.shutdown();
        }
    }

}
