package ru.nsu.ccfit.malinovskii.Model.Message;

import javafx.scene.control.Button;

public class GameInfo {
    private String gameName;
    private String leader;
    private int playersCount;
    private String config;
    private Button joinButton;

    public GameInfo(String gameName, String leader, int playersCount, String config, Button joinButton) {
        this.gameName = gameName;
        this.leader = leader;
        this.playersCount = playersCount;
        this.config = config;
        this.joinButton = joinButton;
    }

    public String getGameName() {
        return gameName;
    }

    public String getLeader() {
        return leader;
    }

    public int getPlayersCount() {
        return playersCount;
    }

    public String getConfig() {
        return config;
    }

    public Button getJoinButton() {
        return joinButton;
    }
}