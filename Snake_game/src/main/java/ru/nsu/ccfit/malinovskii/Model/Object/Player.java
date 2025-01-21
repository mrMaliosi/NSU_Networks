package ru.nsu.ccfit.malinovskii.Model.Object;

import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

public class Player {
    private String playerName;
    private int playerID;
    private SnakesProto.NodeRole playerRole;
    private SnakesProto.PlayerType playerType;
    private int points;
    private Snake snake;

    // Конструктор без параметров
    public Player() {
    }

    // Конструктор с параметрами
    public Player(String playerName, int playerID, SnakesProto.NodeRole playerRole,
                  SnakesProto.PlayerType playerType, int points, Snake snake) {
        this.playerName = playerName;
        this.playerID = playerID;
        this.playerRole = playerRole;
        this.playerType = playerType;
        this.points = points;
        this.snake = snake;
    }

    // Геттеры и сеттеры
    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public int getPlayerID() {
        return playerID;
    }

    public void setPlayerID(int playerID) {
        this.playerID = playerID;
    }

    public SnakesProto.NodeRole getPlayerRole() {
        return playerRole;
    }

    public void setPlayerRole(SnakesProto.NodeRole playerRole) {
        this.playerRole = playerRole;
    }

    public SnakesProto.PlayerType getPlayerType() {
        return playerType;
    }

    public void setPlayerType(SnakesProto.PlayerType playerType) {
        this.playerType = playerType;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public Snake getSnake() {
        return snake;
    }

    public void setSnake(Snake snake) {
        this.snake = snake;
    }

    // Метод для увеличения очков
    public void addPoints(int additionalPoints) {
        this.points += additionalPoints;
    }

    // Метод для проверки, является ли игрок ведущим (MASTER)
    public boolean isMaster() {
        return this.playerRole == SnakesProto.NodeRole.MASTER;
    }

    @Override
    public String toString() {
        return "Player{" +
                "playerName='" + playerName + '\'' +
                ", playerID=" + playerID +
                ", playerRole=" + playerRole +
                ", playerType=" + playerType +
                ", points=" + points +
                ", snake=" + snake +
                '}';
    }
}
