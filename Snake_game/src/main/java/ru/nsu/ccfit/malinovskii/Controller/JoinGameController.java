package ru.nsu.ccfit.malinovskii.Controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.Model.Context.NetworkContext;
import ru.nsu.ccfit.malinovskii.Model.Context.PlayerContext;
import ru.nsu.ccfit.malinovskii.Thread.ClientThread;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class JoinGameController {
    @FXML
    public TextField PlayerNameField;
    @FXML
    public Label ErrorLabel;
    @FXML
    public Button OkButton;
    @FXML
    public CheckBox spectatorCheckBox;

    private SnakesProto.GameAnnouncement gameAnnouncementMsg;

    PlayerContext playerContext;
    GameContext gameContext;
    NetworkContext networkContext;

    @FXML
    public void initialize() {
        gameContext = GameContext.getContext();
        OkButton.setOnAction(e -> {
            try {
                String playerName = PlayerNameField.getText();
                Boolean isSpectator = spectatorCheckBox.isSelected();
                if (playerName == null) {
                    ErrorLabel.setText("ERROR: wrong player name.");
                } else {
                    GameContext gameContext = GameContext.getContext();
                    if (!isSpectator){
                        playerContext = PlayerContext.getPlayerContext(playerName, SnakesProto.NodeRole.NORMAL, SnakesProto.PlayerType.HUMAN);
                    } else {
                        playerContext = PlayerContext.getPlayerContext(playerName, SnakesProto.NodeRole.VIEWER, SnakesProto.PlayerType.HUMAN);
                    }

                    joinGame(gameAnnouncementMsg);

                    // Загрузка новой сцены
                    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ru/nsu/ccfit/malinovskii/view/game-view.fxml"));
                    Parent root = fxmlLoader.load();

                    GameController gameController = fxmlLoader.getController();
                    gameController.initialize(gameContext.getWidth(), gameContext.getHeight()); // Передаём параметры сетки

                    //this.threadContext = ThreadContext.getContext();
                    //this.threadContext.getListener().stop();

                    Stage currentStage = (Stage) OkButton.getScene().getWindow();
                    currentStage.setScene(new Scene(root));
                    currentStage.show();
                }
            } catch (NumberFormatException ex) {
                ErrorLabel.setText("ERROR: invalid numeric value.");
            } catch (IOException ex) {
                ErrorLabel.setText("ERROR: failed to load game scene.");
                ex.printStackTrace();
            }
        });
    }

    public void setGameAnnouncement(SnakesProto.GameAnnouncement game){
        this.gameAnnouncementMsg = game;
    }


    // Метод для подключения к игре
    private void joinGame (SnakesProto.GameAnnouncement game){
        // Получение IP-адреса и порта ведущего
        String leaderAddress = game.getPlayers().getPlayers(0).getIpAddress(); // Адрес ведущего
        int leaderPort = game.getPlayers().getPlayers(0).getPort(); // Порт ведущего

        System.out.println("Joining game: " + game.getGameName());
        System.out.println("Leader Address: " + leaderAddress + ", Port: " + leaderPort);

        // Логика подключения
        try {
            // Здесь реализуется логика подключения к игре
            // Например, создание UDP-сокета и отправка сообщения о присоединении
            InetAddress leaderInetAddress = InetAddress.getByName(leaderAddress);
            DatagramSocket socket = new DatagramSocket();

            networkContext = NetworkContext.getContext();
            networkContext.setSocket(socket);

            // Создание сообщения для отправки
            SnakesProto.GameMessage.JoinMsg joinMsg = SnakesProto.GameMessage.JoinMsg.newBuilder()
                    .setPlayerType(SnakesProto.PlayerType.HUMAN) // Указываем тип игрока
                    .setPlayerName(playerContext.getPlayerName())
                    .setGameName(gameContext.getGameName())
                    .setRequestedRole(playerContext.getPlayerRole()) // Указываем роль
                    .build();

            SnakesProto.GameMessage message = SnakesProto.GameMessage.newBuilder()
                    .setMsgSeq(1) // Последовательный номер сообщения (увеличивать при отправке нового)
                    .setJoin(joinMsg) // Вставляем сообщение о подключении
                    .build();

            byte[] data = message.toByteArray();
            DatagramPacket packet = new DatagramPacket(data, data.length, leaderInetAddress, leaderPort);

            System.out.println(String.valueOf(message));
            // Отправка пакета
            socket.send(packet);
            System.out.println("Join request sent to " + leaderAddress + ":" + leaderPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
