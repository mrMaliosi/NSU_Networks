package ru.nsu.ccfit.malinovskii.Model.Object;

import javafx.scene.paint.Color;

public class Cell {
    private int row;
    private int col;
    private CellType type;
    private Color cellColor;

    public Cell(int row, int col) {
        this.row = row;
        this.col = col;
        this.type = CellType.EMPTY; // По умолчанию ячейка пустая
        this.cellColor = Color.LIGHTGRAY;
    }

    public Cell(int row, int col, CellType type, Color cellColor) {
        this.row = row;
        this.col = col;
        this.type = type; // По умолчанию ячейка пустая
        this.cellColor = cellColor;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public CellType getType() {
        return type;
    }

    public Color getCellColor() {
        return cellColor;
    }

    public void setType(CellType type) {
        this.type = type;
    }

    public void setCell(int row, int col) {
        this.row = row;
        this.col = col;
        this.type = this.type;
        this.cellColor = this.cellColor;
    }

    public void setCell(int row, int col, CellType type) {
        this.row = row;
        this.col = col;
        this.type = type; // По умолчанию ячейка пустая
        this.cellColor = this.cellColor;
    }

    public void setCell(int row, int col, CellType type, Color cellColor) {
        this.row = row;
        this.col = col;
        this.type = type; // По умолчанию ячейка пустая
        this.cellColor = cellColor;
    }

    public void setCellColor(Color cellColor) {
        this.cellColor = cellColor;
    }
}