package ru.nsu.ccfit.malinovskii.Model.Context;

import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.paint.Color;
import ru.nsu.ccfit.malinovskii.Model.Object.*;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import ru.nsu.ccfit.malinovskii.proto.SnakesProto;  // Импорт класса из сгенерированного protobuf
import ru.nsu.ccfit.malinovskii.Model.Object.Snake;  // Импорт класса Snake, который вы используете в проекте
import ru.nsu.ccfit.malinovskii.Model.Object.Player;  // Импорт класса Player (предполагается, что есть такой класс)
import ru.nsu.ccfit.malinovskii.Model.Object.Cell;  // Импорт класса Cell (предполагается, что есть такой класс)

public class GameContext {
    private static volatile GameContext gameContext; // Объявляем volatile для предотвращения проблем при многопоточности

    private String gameName;
    private int width;
    private int height;
    private int foodCount;
    private int tickDelay;

    private Grid grid;
    public final Object lockGameContext = new Object();

    Random random = new Random();

    LinkedList<Snake> snakeList;
    ObservableList<Player> playerList;

    int playerNumber;
    int appleNumber;

    // Для порядкового номера состояния
    private int stateOrder = 0;

    // Приватный конструктор
    private GameContext(String gameName, int width, int height, int foodCount, int tickDelay) {
        this.gameName = gameName;
        this.width = width;
        this.height = height;
        this.foodCount = foodCount;
        this.tickDelay = tickDelay;
        this.grid = new Grid(width, height);
        this.snakeList = new LinkedList<>();
        this.playerList = FXCollections.observableArrayList();
        this.playerNumber = 0;
        this.appleNumber = 0;
    }

    // Публичный статический метод для получения и создания единственного экземпляра
    public static GameContext getContext(String gameName, int width, int height, int foodCount, int tickDelay) {
        if (gameContext == null) { // Проверяем наличие экземпляра
            synchronized (GameContext.class) { // Синхронизация для потокобезопасности
                if (gameContext == null) { // Повторная проверка внутри блока
                    gameContext = new GameContext(gameName, width, height, foodCount, tickDelay);
                }
            }
        }
        return gameContext;
    }

    // Дополнительный метод для получения уже инициализированного контекста
    public static GameContext getContext() {
        if (gameContext == null) {
            throw new IllegalStateException("GameContext has not been initialized yet. Use getContext with parameters to initialize.");
        }
        return gameContext;
    }

    public static void deleteGameContext(){
        gameContext = null;
    }


    public void setGameName(String gameName) {
        this.gameName = gameName;
    }

    public void setGridWidth(int gridWidth) {
        this.width = gridWidth;
    }

    public void setGridHeight(int gridHeight) {
        this.height = gridHeight;
    }

    public void setFoodCount(int foodCount) {
        this.foodCount = foodCount;
    }

    public void setTickDelay(int tickDelay) {
        this.tickDelay = tickDelay;
    }

    // Геттеры
    public String getGameName() {
        return gameName;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFoodCount() {
        return foodCount;
    }

    public int getTickDelay() {
        return tickDelay;
    }

    public int getPlayerNumber() {
        return playerNumber;
    }

    public int getAppleNumber() {
        return appleNumber;
    }

    public int getSnakesCount(){
        return snakeList.size();
    }


    public LinkedList<Snake> getSnakeList() {
        return snakeList;
    }

    public Cell getCell(int row, int col){
        return grid.getCell(row, col);
    }

    public ObservableList<Player> getPlayerList() {
        return playerList;
    }

    public void addSnake(Snake snake) {
        snakeList.add(snake);
        grid.writeSnake(snake);
    }

    public void addPlayer(Player player){
        ++playerNumber;
        addSnake(player.getSnake());
        playerList.add(player);
    }

    public void writeSnake(Snake snake) {
        grid.writeSnake(snake);
    }

    public void writeSnake(Snake snake, int yTailOld, int xTailOld) {
        grid.writeSnake(snake);
        grid.writeEmpty(yTailOld, xTailOld);
    }

    public void setCell(int row, int col, CellType cellType, Color cellColor){
        grid.setCell(row, col, cellType, cellColor);
    }

    public void addApple() {
        int x, y;
        int tryNumber = 0;
        do{
            x = random.nextInt(this.width);
            y = random.nextInt(this.height);
            ++tryNumber;
        } while (grid.getCell(y, x).getType() != CellType.EMPTY || tryNumber > 1500);

        if (grid.getCell(y, x).getType() != CellType.EMPTY){
            for (y = 0; y < this.height; ++y) {
                for (x = 0; x < this.width; ++x) {
                    if (grid.getCell(y, x).getType() == CellType.EMPTY){
                        ++appleNumber;
                        Cell apple = new Cell(y, x, CellType.FOOD, Color.RED);
                        grid.writeApple(apple);
                        return;
                    }
                }
            }
        }

        if (grid.getCell(y, x).getType() == CellType.EMPTY) {
            ++appleNumber;
            Cell apple = new Cell(y, x, CellType.FOOD, Color.RED);
            grid.writeApple(apple);
            return;
        }
    }

    public void removeApple(){
        --appleNumber;
    }

    public void addPoint(Snake snake, int point){
        Player player = findPlayerBySnake(snake);
        player.addPoints(point);
    }

    public Player findPlayerBySnake(Snake snake) {
        for (Player player : this.playerList) {
            if (player.getSnake().getBodyColor().equals(snake.getBodyColor())) {
                return player;
            }
        }
        throw new IllegalArgumentException("Player with the specified snake color not found.");
    }

    public Player findPlayerByColor(Color color) {
        for (Player player : this.playerList) {
            if (player.getSnake().getBodyColor().equals(color)) {
                return player;
            }
        }
        throw new IllegalArgumentException("Player with the specified snake color not found.");
    }

    public Snake findSnakeByID(int id) {
        for (Snake snake : snakeList) {
            if (colorToId(snake.getBodyColor()) == id){
                return snake;
            }
        }
        throw new IllegalArgumentException("Player with the specified snake color not found.");
    }


    public int getScoreByPlayerID(int id){
        for (Player player : playerList){
            if (player.getPlayerID() == id){
                return player.getPoints();
            }
        }
        throw new IllegalArgumentException("Player on ID not found.");
    }

    public void removeSnake(Snake snake){
        for (Cell cell : snake.getBody()){
            int isApple = random.nextInt(2);
            Cell gridCell = grid.getCell(cell.getRow(), cell.getCol());
            if (isApple == 1){
                gridCell.setCell(cell.getRow(), cell.getCol(), CellType.FOOD, Color.RED);
                ++appleNumber;
            } else {
                gridCell.setCell(cell.getRow(), cell.getCol(), CellType.EMPTY, Color.LIGHTGRAY);
            }
            grid.setCell(cell.getRow(), cell.getCol(), gridCell);
        }
        Player player = findPlayerBySnake(snake);
        playerList.remove(player);
        snakeList.remove(snake);
    }

    // Метод для увеличения порядкового номера состояния
    public void incrementStateOrder() {
        stateOrder++;
    }

    // Метод получения текущего порядкового номера состояния
    public int getStateOrder() {
        return stateOrder;
    }

    public LinkedList<SnakesProto.GameState.Snake> getSnakes() {
        LinkedList<SnakesProto.GameState.Snake> protobufSnakes = new LinkedList<>();

        for (Snake snake : snakeList) {
            SnakesProto.GameState.Snake.Builder snakeBuilder = SnakesProto.GameState.Snake.newBuilder();

            // Устанавливаем player_id
            Player player = findPlayerBySnake(snake); // Предполагаем, что у нас есть метод для поиска игрока по змее
            snakeBuilder.setPlayerId(player.getPlayerID());

            // Устанавливаем состояние змеи
            SnakesProto.GameState.Snake.SnakeState state = SnakesProto.GameState.Snake.SnakeState.ALIVE; // Тут нужно использовать логику для определения состояния (ALIVE или ZOMBIE)
            snakeBuilder.setState(state);

            // Устанавливаем список точек змеи
            LinkedList<Cell> body = snake.getBody();
            for (int i = 0; i < body.size(); i++) {
                Cell cell = body.get(i);
                SnakesProto.GameState.Coord.Builder coordBuilder = SnakesProto.GameState.Coord.newBuilder();
                coordBuilder.setX(cell.getCol());
                coordBuilder.setY(cell.getRow());

                // Если не первая точка, добавляем смещение относительно предыдущей точки
                if (i > 0) {
                    coordBuilder.setX(cell.getCol());
                    coordBuilder.setY(cell.getRow());
                }

                snakeBuilder.addPoints(coordBuilder.build());
            }

            // Устанавливаем направление головы змеи
            snakeBuilder.setHeadDirection(snake.getDirection());

            // Добавляем змею в список
            protobufSnakes.add(snakeBuilder.build());
        }

        return protobufSnakes;
    }

    // Метод для получения еды
    public LinkedList<SnakesProto.GameState.Coord> getFood() {
        LinkedList<SnakesProto.GameState.Coord> foodList = new LinkedList<>();
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                Cell cell = grid.getCell(y, x);
                if (cell.getType() == CellType.FOOD) {
                    SnakesProto.GameState.Coord cord = SnakesProto.GameState.Coord.newBuilder().setX(y).setY(x).build();
                    foodList.add(cord);
                }
            }
        }
        return foodList;
    }

    public void setEmptyGrid(){
        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                grid.setCell(y, x, CellType.EMPTY, Color.LIGHTGRAY);
            }
        }
    }

    public void writeGame(SnakesProto.GameMessage.StateMsg stateMsg) {
        // 1. Получаем список змей из состояния игры
        List<SnakesProto.GameState.Snake> snakes = stateMsg.getState().getSnakesList();

        // 2. Очищаем текущие змеи (если нужно) и записываем новые
        synchronized (gameContext.lockGameContext) { // Синхронизация для потокобезопасности
            this.snakeList.clear();

            // Обрабатываем каждую змею и записываем её в контекст игры
            for (SnakesProto.GameState.Snake protoSnake : snakes) {
                Snake snake = new Snake(); // Создаём новую змею
                int isFirst = 1;
                for (SnakesProto.GameState.Coord coord : protoSnake.getPointsList()) {
                    Cell cell;
                    if (isFirst == 1) {
                        cell = new Cell(coord.getY(), coord.getX(), CellType.HEAD, idToColor(protoSnake.getPlayerId()).darker());
                        isFirst = 0;
                    } else {
                        cell = new Cell(coord.getY(), coord.getX(), CellType.SNAKE, idToColor(protoSnake.getPlayerId()));
                    }
                    snake.addCell(cell);
                }
                snake.setDirection(protoSnake.getHeadDirection());

                snake.setId(protoSnake.getPlayerId());
                this.snakeList.add(snake);
                this.grid.writeSnake(snake);
            }

            this.playerNumber = 0;
            this.playerList.clear();
            // 3. Записываем состояние игроков в контекст игры
            List<SnakesProto.GamePlayer> players = stateMsg.getState().getPlayers().getPlayersList();
            for (SnakesProto.GamePlayer gamePlayer : players) {
                Player player = new Player(
                        gamePlayer.getName(),
                        gamePlayer.getId(),
                        gamePlayer.getRole(),
                        gamePlayer.getType(),
                        gamePlayer.getScore(),
                        findSnakeByRealID(gamePlayer.getId())
                );

                this.playerList.add(player);
            }

            // 4. Обрабатываем еду
            this.appleNumber = 0;
            List<SnakesProto.GameState.Coord> foodList = stateMsg.getState().getFoodsList();
            for (SnakesProto.GameState.Coord foodCoord : foodList) {
                // Добавляем еду в поле
                Cell foodCell = new Cell(foodCoord.getX(), foodCoord.getY(), CellType.FOOD, Color.RED);
                this.grid.writeApple(foodCell); // Записываем яблоко на поле
                this.appleNumber++; // Увеличиваем количество яблок
            }
        }
    }

    public Snake getLastSnake(){
        return snakeList.getLast();
    }

    // Метод для преобразования цвета в уникальный ID
    public static int colorToId(Color color) {
        int red = (int) (color.getRed() * 255);   // Получаем значение красного компонента (от 0 до 255)
        int green = (int) (color.getGreen() * 255); // Получаем значение зелёного компонента (от 0 до 255)
        int blue = (int) (color.getBlue() * 255);   // Получаем значение синего компонента (от 0 до 255)
        int alpha = (int) (color.getOpacity() * 255); // Получаем значение альфа-канала (от 0 до 255)

        // Объединяем значения в одно число
        return (alpha << 24) | (red << 16) | (green << 8) | blue; // Собираем ID
    }

    // Метод для преобразования ID обратно в цвет
    public static Color idToColor(int id) {
        int alpha = (id >> 24) & 0xFF;  // Извлекаем альфа-канал
        int red = (id >> 16) & 0xFF;    // Извлекаем красный компонент
        int green = (id >> 8) & 0xFF;   // Извлекаем зелёный компонент
        int blue = id & 0xFF;           // Извлекаем синий компонент

        return Color.rgb(red, green, blue, alpha / 255.0); // Преобразуем обратно в цвет
    }

    public Snake findSnakeByRealID(int id){
        for (Snake snake : snakeList) {
            if (snake.getId() == id){
                return snake;
            }
        }
        throw new IllegalArgumentException("Snake with the specified player ID not found.");
    }

    @Override
    public String toString() {
        return "GameContext{" +
                "gameName='" + gameName + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", foodCount=" + foodCount +
                ", tickDelay=" + tickDelay +
                '}';
    }

    public boolean updatePlayerDirection(int senderPlayerId, SnakesProto.Direction newDirection) {
        for (Snake snake : snakeList){
            if (snake.getId() == senderPlayerId){
                snake.setNewDirection(newDirection);
                return true;
            }
        }
        return false;
    }
}
