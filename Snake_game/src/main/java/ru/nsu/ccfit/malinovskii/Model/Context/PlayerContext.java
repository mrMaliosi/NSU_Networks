package ru.nsu.ccfit.malinovskii.Model.Context;

import ru.nsu.ccfit.malinovskii.Model.Object.Snake;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.util.Random;

public class PlayerContext {
    private static volatile PlayerContext playerContext; // Объявляем volatile для предотвращения проблем при многопоточности

    private String playerName;
    private SnakesProto.NodeRole playerRole;
    private SnakesProto.PlayerType playerType;
    private int playerID;
    private Snake snake;

    Random random = new Random();

    // Приватный конструктор
    private PlayerContext(String playerName, SnakesProto.NodeRole playerRole, SnakesProto.PlayerType playerType) {
        this.playerName = playerName;
        this.playerRole = playerRole;
        this.playerType = playerType;
    }

    // Публичный статический метод для получения единственного экземпляра
    public static PlayerContext getPlayerContext(String playerName, SnakesProto.NodeRole playerRole, SnakesProto.PlayerType playerType) {
        if (playerContext == null) { // Проверяем наличие экземпляра
            synchronized (GameContext.class) { // Синхронизация для потокобезопасности
                if (playerContext == null) { // Повторная проверка внутри блока
                    playerContext = new PlayerContext(playerName, playerRole, playerType);
                }
            }
        }
        return playerContext;
    }

    // Дополнительный метод для получения уже инициализированного контекста
    public static PlayerContext getPlayerContext() {
        if (playerContext == null) {
            throw new IllegalStateException("PlayerContext has not been initialized yet. Use getContext with parameters to initialize.");
        }
        return playerContext;
    }

    public static void deletePlayerContext(){
        playerContext = null;
    }


    // Геттеры
    public String getPlayerName() {
        return playerName;
    }

    public SnakesProto.NodeRole getPlayerRole() {
        return playerRole;
    }

    public SnakesProto.PlayerType getPlayerType(){
        return playerType;
    }

    public int getPlayerID() {
        return playerID;
    }

    public void setPlayerID(int playerID) {
        this.playerID = playerID;
    }

    public Snake getSnake() {
        return snake;
    }

    public void setSnake(Snake snake) {
        this.snake = snake;
    }

    public void setPlayerRole(SnakesProto.NodeRole receiverRole) {
        this.playerRole = receiverRole;
    }
}