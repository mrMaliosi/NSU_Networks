package ru.nsu.ccfit.malinovskii.Model;

import ru.nsu.ccfit.malinovskii.Model.Object.Grid;
import ru.nsu.ccfit.malinovskii.Model.Object.CellType;

public class GridInitializer {

    private Grid grid; // Сетка игрового поля

    // Метод для инициализации сетки на основе параметров GameConfig
    public void initializeScene() {
        /*
        int width = config.hasWidth() ? config.getWidth() : 40;  // Если значение отсутствует, используется значение по умолчанию
        int height = config.hasHeight() ? config.getHeight() : 30;

        // Проверка на допустимые размеры
        if (width < 10 || width > 100 || height < 10 || height > 100) {
            throw new IllegalArgumentException("Недопустимые размеры поля. Ширина и высота должны быть от 10 до 100.");
        }

        // Создаем сетку с заданными размерами
        grid = new Grid(height, width);

        // Инициализируем статическое количество еды
        int foodStatic = config.hasFoodStatic() ? config.getFoodStatic() : 1;
        initializeStaticFood(foodStatic);

        System.out.println("Игровая сцена успешно инициализирована.");
         */
    }

    // Метод для размещения статической еды на поле
    private void initializeStaticFood(int foodStatic) {
        int placedFood = 0;

        while (placedFood < foodStatic) {
            int row = (int) (Math.random() * grid.getRows());
            int col = (int) (Math.random() * grid.getCols());

            if (grid.getCell(row, col).getType() == CellType.EMPTY) {
                grid.getCell(row, col).setType(CellType.FOOD);
                placedFood++;
            }
        }

        System.out.println("Размещено " + foodStatic + " клеток с едой.");
    }

    // Получить сетку
    public Grid getGrid() {
        return grid;
    }
}
