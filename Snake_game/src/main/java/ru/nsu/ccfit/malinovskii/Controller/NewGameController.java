package ru.nsu.ccfit.malinovskii.Controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import ru.nsu.ccfit.malinovskii.Model.Context.GameContext;
import ru.nsu.ccfit.malinovskii.Model.Context.PlayerContext;
import ru.nsu.ccfit.malinovskii.Model.Context.ThreadContext;
import ru.nsu.ccfit.malinovskii.proto.SnakesProto;

import java.io.IOException;

public class NewGameController {
    @FXML
    public TextField GameNameField;
    @FXML
    public TextField PlayerNameField;
    @FXML
    public TextField GridWidthField;
    @FXML
    public TextField GridHeightField;
    @FXML
    public TextField FoodCountField;
    @FXML
    public TextField TickDelayField;
    @FXML
    public Label ErrorLabel;
    @FXML
    public Button OkButton;

    ThreadContext threadContext;

    @FXML
    public void initialize() {
        OkButton.setOnAction(e -> {
            try {
                String gameName = GameNameField.getText();
                String playerName = PlayerNameField.getText();
                int gridWidth = Integer.parseInt(GridWidthField.getText());
                int gridHeight = Integer.parseInt(GridHeightField.getText());
                int foodCount = Integer.parseInt(FoodCountField.getText());
                int tickDelay = Integer.parseInt(TickDelayField.getText());
                if (gameName == null){
                    ErrorLabel.setText("ERROR: wrong game name.");
                } else if (playerName == null){
                    ErrorLabel.setText("ERROR: wrong player name.");
                } else if (gridWidth < 10 || gridWidth > 100) {
                    ErrorLabel.setText("ERROR: wrong width.");
                } else if (gridHeight < 10 || gridHeight > 100) {
                    ErrorLabel.setText("ERROR: wrong height.");
                } else if (foodCount < 0 || foodCount > 100) {
                    ErrorLabel.setText("ERROR: wrong foodCount.");
                } else if (tickDelay < 100 || tickDelay > 3000) {
                    ErrorLabel.setText("ERROR: wrong tickDelay.");
                } else {

                    GameContext gameContext = GameContext.getContext(gameName, gridWidth, gridHeight, foodCount, tickDelay);
                    PlayerContext playerContext = PlayerContext.getPlayerContext(playerName, SnakesProto.NodeRole.MASTER, SnakesProto.PlayerType.HUMAN);

                    // Загрузка новой сцены
                    FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/ru/nsu/ccfit/malinovskii/view/game-view.fxml"));
                    Parent root = fxmlLoader.load();

                    GameController gameController = fxmlLoader.getController();
                    gameController.initialize(gridWidth, gridHeight); // Передаём параметры сетки

                    //this.threadContext = ThreadContext.getContext();
                    //this.threadContext.getListener().stop();

                    Stage currentStage = (Stage) OkButton.getScene().getWindow();
                    currentStage.setScene(new Scene(root));
                    currentStage.show();
                }
            } catch (NumberFormatException ex) {
                ErrorLabel.setText("ERROR: invalid numeric value.");
            } catch (IOException ex) {
                ErrorLabel.setText("ERROR: failed to load game scene.");
                ex.printStackTrace();
            }
        });

        //exitButton.setOnAction();
    }
}
