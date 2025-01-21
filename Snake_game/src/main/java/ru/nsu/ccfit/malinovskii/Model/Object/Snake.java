package ru.nsu.ccfit.malinovskii.Model.Object;

import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import javafx.scene.paint.Color;

public class Snake {
    private int id;
    private Cell head;
    private LinkedList<Cell> body;
    private SnakesProto.Direction direction;
    private SnakesProto.Direction newDirection;

    // Цвета змеи
    private Color bodyColor;
    private Color headColor;

    // Список использованных цветов для уникальности
    private static final HashSet<Color> usedColors = new HashSet<>();
    private static final Random random = new Random();

    public Snake() {
        this.head = null;
        this.body = new LinkedList<>();
        this.direction = null;
        this.newDirection = null;

        // Генерируем уникальные цвета для змеи
        this.bodyColor = generateUniqueColor();
        this.headColor = bodyColor.darker(); // Для головы используем более тёмный оттенок
    }

    public LinkedList<Cell> getSnakeBody() {
        return body;
    }

    public SnakesProto.Direction getDirection(){
        return direction;
    }


    public void addCell(Cell cell) {
        if (this.head == null) {
            head = cell;
        }
        body.add(cell);
    }

    public void setDirection(SnakesProto.Direction direction) {
        this.direction = direction;
        this.newDirection = direction;
    }

    public Cell getHead(){
        return head;
    }

    public void setHead(Cell head){
        this.head = head;
    }

    public LinkedList<Cell> getBody(){
        return this.body;
    }

    public void setBody(LinkedList<Cell> body){
        this.body = body;
    }

    public Color getBodyColor() {
        return bodyColor;
    }

    public Color getHeadColor() {
        return headColor;
    }

    private Color generateUniqueColor() {
        Color newColor;
        do {
            newColor = Color.color(random.nextDouble(), random.nextDouble(), random.nextDouble());
        } while (usedColors.contains(newColor));
        usedColors.add(newColor);
        return newColor;
    }

    public SnakesProto.Direction getNewDirection() {
        return newDirection;
    }

    public void setNewDirection(SnakesProto.Direction newDirection) {
        this.newDirection = newDirection;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}