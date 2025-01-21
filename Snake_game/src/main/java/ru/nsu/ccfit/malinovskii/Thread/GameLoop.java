package ru.nsu.ccfit.malinovskii.Model;

import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.paint.Color;
import ru.nsu.ccfit.malinovskii.Controller.GameController;
import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.Model.Object.Cell;
import ru.nsu.ccfit.malinovskii.Model.Object.CellType;
import ru.nsu.ccfit.malinovskii.Model.Object.Player;
import ru.nsu.ccfit.malinovskii.Model.Object.Snake;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

public class GameLoop {

    private final GameContext gameContext;

    public GameLoop() {
        this.gameContext = GameContext.getContext();
    }


    public void gameThread() {
        moveSnakes(); // Перемещаем змей
        respawnApple(); // Респавн яблока
    }

    private void moveSnakes() {
        //System.out.println("Snakes are moving...");
        LinkedList <Snake> snakeList = gameContext.getSnakeList();
        for (Snake snake : snakeList){
            moveSnake(snake);
        }
    }

    //TO DO: добавить обработку если змея убита (проверка на живость в начале)
    private void moveSnake(Snake snake) {
        Cell head = snake.getHead(); // Получаем текущую голову змеи
        LinkedList<Cell> body = snake.getBody(); // Получаем тело змеи
        int y = head.getRow();
        int x = head.getCol();
        Boolean isFood = false;
        Boolean isSnake = false;
        System.out.println("[LOG]: Head: (" + String.valueOf(x) + ", " + String.valueOf(y) + "). Direction: " + String.valueOf(snake.getNewDirection()));

        // Рассчитываем новое положение головы
        switch (snake.getNewDirection()) {
            case SnakesProto.Direction.UP:
                snake.setDirection(SnakesProto.Direction.UP);
                y = (y - 1 + gameContext.getHeight()) % gameContext.getHeight(); // Цикличность по вертикали
                break;
            case SnakesProto.Direction.DOWN:
                snake.setDirection(SnakesProto.Direction.DOWN);
                y = (y + 1) % gameContext.getHeight(); // Цикличность по вертикали
                break;
            case SnakesProto.Direction.LEFT:
                snake.setDirection(SnakesProto.Direction.LEFT);
                x = (x - 1 + gameContext.getWidth()) % gameContext.getWidth(); // Цикличность по горизонтали
                break;
            case SnakesProto.Direction.RIGHT:
                snake.setDirection(SnakesProto.Direction.RIGHT);
                x = (x + 1) % gameContext.getWidth(); // Цикличность по горизонтали
                break;
            default:
                // Если направление не установлено, не двигаем змею
                return;
        }

        switch (gameContext.getCell(y, x).getType()){
            case CellType.FOOD:
                isFood = true;
                break;
            case CellType.HEAD:
            case CellType.SNAKE:
                isSnake = true;
                break;
            default:
                break;
        }

        if (isFood){
            Cell newHead = new Cell(y, x, CellType.HEAD, body.getFirst().getCellColor());
            body.getFirst().setCellColor(body.get(1).getCellColor());
            body.getFirst().setType(CellType.SNAKE);
            body.addFirst(newHead);
            gameContext.removeApple();
            gameContext.addPoint(snake, 1);
            gameContext.writeSnake(snake);

            // Обновляем положение головы
            head = newHead;
            snake.setHead(newHead);
            System.out.println("[LOG]: Move snake. HeadNew: (" + String.valueOf(x) + ", " + String.valueOf(y) + ")" + " Direction: " + String.valueOf(snake.getDirection()) + " New direction: " + String.valueOf(snake.getNewDirection()));
        } else if (isSnake){
            System.out.println("[LOG]: Remove snake.");
            Color color =  gameContext.getCell(y, x).getCellColor();
            if (color != snake.getBodyColor()){
                Player player = gameContext.findPlayerBySnake(snake);
                Player anotherPlayer = gameContext.findPlayerByColor(color);
                gameContext.addPoint(anotherPlayer.getSnake(), player.getPoints());
            }
            gameContext.removeSnake(snake);
        } else{
            // Сдвигаем все клетки тела змеи
            int yTailOld = body.getLast().getRow();
            int xTailOld = body.getLast().getCol();

            if (!body.isEmpty()) {
                for (int i = body.size() - 1; i > 0; i--) {
                    body.get(i).setCell(body.get(i - 1).getRow(), body.get(i - 1).getCol());
                    Cell bodyCell = body.get(i);
                    //gameContext.setCell(bodyCell.getRow(), bodyCell.getCol(), bodyCell.getType(), bodyCell.getCellColor());
                }
                // Первая часть тела занимает старое место головы
                body.get(0).setCell(y, x);
                Cell bodyCell = body.get(0);
                //gameContext.setCell(y, x, bodyCell.getType(), bodyCell.getCellColor());
                gameContext.writeSnake(snake, yTailOld, xTailOld);
            }

            // Обновляем положение головы
            head.setCell(y, x);
            System.out.println("[LOG]: Move snake. HeadNew: (" + String.valueOf(x) + ", " + String.valueOf(y) + ")");
        }
    }

    private void respawnApple() {
        for (int i = 0; i < gameContext.getFoodCount() + gameContext.getSnakesCount() - gameContext.getAppleNumber(); ++i) {
            System.out.println("[LOG]: Respawning apple...");
            gameContext.addApple();
        }
    }
}