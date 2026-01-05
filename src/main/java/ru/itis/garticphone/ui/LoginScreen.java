package ru.itis.garticphone.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.itis.garticphone.client.ClientConnection;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

public class LoginScreen extends Application {

    AppData appData = new AppData();

    private GuessDrawingScreen guessScreen;
    private Stage stage;

    private VBox layout = new VBox(12);
    private Scene lobbyScene;

    private Label lobbyTitle = new Label("Угадай рисунок");

    private Label enterName = new Label("Введите имя:");
    private TextField nameField = new TextField();

    private Label enterRoom = new Label("Введите код комнаты:");
    private TextField roomIdField = new TextField();

    private Button enter = new Button("Войти");
    private Button create = new Button("Создать комнату");

    private Button exit = new Button("Выйти");
    private Label error = new Label();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Gartic Phone");

        VBox nameBox = new VBox(6, enterName, nameField);
        nameBox.setAlignment(Pos.CENTER);
        nameBox.setMaxWidth(320);

        VBox joinBlock = new VBox(8,
                enterRoom,
                roomIdField,
                enter
        );
        joinBlock.setAlignment(Pos.CENTER);
        joinBlock.setMaxWidth(320);

        VBox createBlock = new VBox(8, create);
        createBlock.setAlignment(Pos.CENTER);
        createBlock.setMaxWidth(320);

        VBox actions = new VBox(20, joinBlock, createBlock);
        actions.setAlignment(Pos.CENTER);

        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(40));
        lobbyTitle.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");
        error.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        layout.setSpacing(5);
        layout.getChildren().addAll(
                lobbyTitle,
                nameBox,
                actions,
                error,
                exit
        );

        lobbyScene = new Scene(layout, 800, 600);

        enter.setOnAction(e -> joinExistingRoom());
        create.setOnAction(e -> createRoom());
        exit.setOnAction(e -> stage.close());

        stage.setScene(lobbyScene);
        stage.show();
    }

    private void joinExistingRoom() {
        error.setText("");

        String playerName = nameField.getText() == null ? "" : nameField.getText().trim();
        if (playerName.isBlank()) {
            error.setText("Введите имя");
            return;
        }

        int roomId = getRoomId();
        if (roomId == -1) return;

        appData.isHost = false;
        connect(playerName, roomId);
    }

    private void createRoom() {
        error.setText("");

        String playerName = nameField.getText() == null ? "" : nameField.getText().trim();
        if (playerName.isBlank()) {
            error.setText("Введите имя");
            return;
        }

        int roomId = ThreadLocalRandom.current().nextInt(100_000, 999_999);
        roomIdField.setText(String.valueOf(roomId));

        appData.isHost = true;
        connect(playerName, roomId);
    }

    private void connect(String playerName, int roomId) {
        try {
            appData.clientConnection = new ClientConnection("localhost", 8080);
            appData.playerName = playerName;
            appData.roomId = roomId;

            appData.clientConnection.startListening(msg -> {
                if (guessScreen != null) {
                    guessScreen.handleIncoming(msg);
                } else {
                    handleServerMessage(msg);
                }
            });

            Message join = new Message(
                    MessageType.JOIN,
                    roomId,
                    0,
                    playerName,
                    "GUESS_DRAWING"
            );
            appData.clientConnection.send(join);

            enter.setDisable(true);
            create.setDisable(true);

        } catch (IOException e) {
            error.setText("Не вышло подключиться: " + e.getMessage());
            enter.setDisable(false);
            create.setDisable(false);
        }
    }

    private void handleServerMessage(Message msg) {
        if (msg == null || msg.getType() == null) return;

        switch (msg.getType()) {

            case ERROR -> Platform.runLater(() -> {
                error.setText("ошибка: " + msg.getPayload());
                enter.setDisable(false);
                create.setDisable(false);
            });

            case PLAYER_STATUS -> Platform.runLater(() -> {
                guessScreen = new GuessDrawingScreen(appData, () -> {
                    guessScreen = null;
                    stage.setScene(lobbyScene);

                    enter.setDisable(false);
                    create.setDisable(false);
                    error.setText("");
                });

                stage.setScene(new Scene(guessScreen.getRoot(), 1000, 600));
            });

            case END_GAME -> Platform.runLater(() -> {
                error.setText("Игра завершена сервером!");
                enter.setDisable(false);
                create.setDisable(false);
            });

            default -> { }
        }
    }

    private int getRoomId() {
        try {
            return Integer.parseInt(roomIdField.getText().trim());
        } catch (Exception e) {
            error.setText("неверный код комнаты");
            return -1;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
