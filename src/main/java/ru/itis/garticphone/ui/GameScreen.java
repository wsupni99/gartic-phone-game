package ru.itis.garticphone.ui;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class GameScreen extends Application {

    private Stage primaryStage;
    private Scene lobbyScene;

    private Scene guessDrawingScene;
    private Scene deafPhoneScene;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        stage.setTitle("Gartic Phone");

        createLobbyScene();

        stage.setScene(lobbyScene);
        stage.show();
    }

    private void createLobbyScene() {
        Label title = new Label("Gartic Phone");

        Label nameLabel = new Label("Твоё имя:");
        TextField nameField = new TextField("Player");
        nameField.setMaxWidth(200);

        Button guessButton = new Button("Угадай рисунок (Guess Drawing)");
        Button deafButton = new Button("Глухой телефон (Deaf Phone)");

        Label statusLabel = new Label("Не подключен");
        guessButton.setOnAction(e -> {
            createGuessDrawingScene(nameField.getText());
            primaryStage.setScene(guessDrawingScene);
            statusLabel.setText("Режим: Угадай рисунок");
        });

        deafButton.setOnAction(e -> {
            createDeafPhoneScene(nameField.getText());
            primaryStage.setScene(deafPhoneScene);
            statusLabel.setText("Режим: Глухой телефон");
        });

        Button backButton = new Button("Назад в лобби");

        VBox layout = new VBox(20);
        layout.setPadding(new Insets(40));
        layout.setAlignment(Pos.CENTER);
        layout.getChildren().addAll(title, nameLabel, nameField, guessButton, deafButton, statusLabel);

        lobbyScene = new Scene(layout, 600, 500);
    }

    private void createGuessDrawingScene(String playerName) {
        Label title = new Label("Режим: Угадай рисунок");
        title.setStyle("-fx-font-size: 24px;");

        Label info = new Label("Имя: " + playerName + "\n(Здесь будет Canvas для рисования/угадывания)");

        Button backButton = new Button("Вернуться в лобби");
        backButton.setOnAction(e -> primaryStage.setScene(lobbyScene));

        VBox layout = new VBox(20, title, info, backButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));

        guessDrawingScene = new Scene(layout, 800, 600);
    }

    private void createDeafPhoneScene(String playerName) {
        Label title = new Label("Режим: Глухой телефон");
        title.setStyle("-fx-font-size: 24px;");

        Label info = new Label("Имя: " + playerName + "\n(Здесь будут раунды текст/рисунок)");

        Button backButton = new Button("Вернуться в лобби");
        backButton.setOnAction(e -> primaryStage.setScene(lobbyScene));

        VBox layout = new VBox(20, title, info, backButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));

        deafPhoneScene = new Scene(layout, 800, 600);
    }

    public static void main(String[] args) {
        launch(args);
    }
}