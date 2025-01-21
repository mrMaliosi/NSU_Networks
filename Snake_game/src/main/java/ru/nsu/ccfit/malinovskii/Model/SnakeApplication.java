package ru.nsu.ccfit.malinovskii.Model;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.*;
import java.net.URL;


public class SnakeApplication extends Application {
    public static Stage stage;
    public static FXMLLoader loader = new FXMLLoader();
    @Override
    public void start(Stage primaryStage) throws Exception {
        stage = primaryStage;
        URL xmlUrl = getClass().getResource("/ru/nsu/ccfit/malinovskii/view/menu-view.fxml");
        if (xmlUrl == null){
            System.out.println("Error. File menu-view.fxml not found");
            Platform.exit();
            System.exit(0);
        }

        System.out.println(xmlUrl);
        loader.setLocation(xmlUrl);
        try {
            Parent root = loader.load();
            stage.setScene(new Scene(root));
            stage.setTitle("Snakers");
            InputStream iconStream = getClass().getResourceAsStream("/ru/nsu/ccfit/malinovskii/Icons/snake.png");
            Image image = new Image(iconStream);
            primaryStage.getIcons().add(image);
            stage.show();
        } catch (IOException e) {
            // Обработка ошибок, связанных с загрузкой FXML файла
            System.err.println("Ошибка при загрузке FXML файла: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            // Обработка других неожиданных ошибок
            System.err.println("Неизвестная ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        Platform.exit();
        System.exit(0);
    }
}
