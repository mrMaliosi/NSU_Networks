package ru.nsu.ccfit.malinovskii;

import javafx.application.Application;
import ru.nsu.ccfit.malinovskii.Model.SnakeApplication;

import static javafx.application.Application.launch;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static SnakeApplication application;

    public static void main(String[] args) {
        application = new SnakeApplication();

        try {
            Application.launch(SnakeApplication.class, args);
        } catch (Exception e) {
            e.printStackTrace();
            //logger.error("Exception: ", e);
        }
    }
}