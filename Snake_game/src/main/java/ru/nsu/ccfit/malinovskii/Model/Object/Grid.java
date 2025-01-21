package ru.nsu.ccfit.malinovskii.Model.Object;

import javafx.scene.paint.Color;

import java.util.LinkedList;

public class Grid {
    private Cell[][] cells;
    private int cols;
    private int rows;

    // Конструктор для создания сетки с заданными размерами
    public Grid(int cols, int rows) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Размеры сетки должны быть больше нуля.");
        }

        this.cols = cols;
        this.rows = rows;
        cells = new Cell[rows][cols];
        initializeGrid();
    }

    // Инициализация ячеек в сетке
    private void initializeGrid() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cells[row][col] = new Cell(row, col);
            }
        }
    }

    // Получить ячейку по координатам
    public Cell getCell(int row, int col) {
        if (isWithinBounds(row, col)) {
            return cells[row][col];
        }
        throw new IndexOutOfBoundsException("Координаты вне границ сетки.");
    }

    // Установить значение ячейки
    public void setCell(int row, int col, Cell cell) {
        if (isWithinBounds(row, col)) {
            cells[row][col] = cell;
        } else {
            throw new IndexOutOfBoundsException("Координаты вне границ сетки.");
        }
    }

    public void setCell(int row, int col, CellType cellType, Color cellColor) {
        if (isWithinBounds(row, col)) {
            cells[row][col].setType(cellType);
            cells[row][col].setCellColor(cellColor);
        } else {
            throw new IndexOutOfBoundsException("Координаты вне границ сетки.");
        }
    }

    // Проверка границ сетки
    private boolean isWithinBounds(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    // Получить количество строк
    public int getRows() {
        return rows;
    }

    // Получить количество столбцов
    public int getCols() {
        return cols;
    }

    public void writeSnake(Snake snake){
        LinkedList <Cell> snakeBody = snake.getSnakeBody();
        for (Cell element : snakeBody) {
            int x = element.getCol();
            int y = element.getRow();
            cells[y][x] = element;
        }
    }

    public void writeApple(Cell apple){
        int x = apple.getCol();
        int y = apple.getRow();
        cells[y][x] = apple;
    }

    public void writeEmpty(int row, int col){
        Cell cell = new Cell(row, col, CellType.EMPTY, Color.LIGHTGRAY);
        cells[row][col] = cell;
    }
}