package ru.nsu.ccfit.malinovskii.Model;

import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

public class GameConfigBuilder {
    public static SnakesProto.GameConfig createGameConfig(GameContext gameContext) {
        return SnakesProto.GameConfig.newBuilder()
                .setWidth(gameContext.getWidth()) // Ширина поля
                .setHeight(gameContext.getHeight()) // Высота поля
                .setFoodStatic(gameContext.getFoodCount()) // Статическое количество еды
                .setStateDelayMs(gameContext.getTickDelay()) // Задержка между тиками
                .build();
    }
}