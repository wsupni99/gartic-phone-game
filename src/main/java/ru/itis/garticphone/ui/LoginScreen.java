package ru.itis.garticphone.ui;

import com.google.gson.Gson;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import ru.itis.garticphone.client.ClientConnection;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class LoginScreen extends Application {

    AppData appData = new AppData();
    private final Gson gson = new Gson();
    private GuessDrawingScreen guessScreen;

    private Stage stage;
    private VBox layout = new VBox(10);
    private Scene lobbyScene;

    private Label lobbyTitle = new Label("Gartic Phone");

    private Label enterName = new Label("Введите имя:");
    private TextField nameField = new TextField();

    private Label enterRoom = new Label("Введите айди комнаты:");
    private TextField roomIdField = new TextField();
    private Button enter = new Button("Войти");

    private Label orLabel = new Label("ИЛИ Выберите режим:");
    private RadioButton rbGuess = new RadioButton("Угадай рисунок");
    private RadioButton rbDeaf = new RadioButton("Глухой телефон");
    private Button create = new Button("Создать");

    private Button exit = new Button("Выйти");
    private Label error = new Label();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Gartic Phone");

        lobbyScene = new Scene(layout, 800, 600);
        layout.setAlignment(Pos.CENTER);

        layout.getChildren().addAll(
                lobbyTitle,
                enterName, nameField,

                enterRoom, roomIdField,
                enter,

                orLabel,
                rbGuess, rbDeaf,
                create,

                error,
                exit
        );

        ToggleGroup modes = new ToggleGroup();
        rbGuess.setToggleGroup(modes);
        rbDeaf.setToggleGroup(modes);
        rbGuess.setSelected(true);

        enter.setOnAction(e -> joinExistingRoom());
        create.setOnAction(e -> createRoom());
        exit.setOnAction(e -> stage.close());

        stage.setScene(lobbyScene);
        stage.show();
    }


    private void joinExistingRoom() {
        error.setText("");

        String playerName = nameField.getText() == null ? "" : nameField.getText();
        if (playerName == null) return;

        int roomId = getRoomId();
        if (roomId == -1) return;

        // при входе ставим режим по умолчанию
        String mode = "GUESS_DRAWING";

        appData.isHost = false;
        connect(playerName, roomId, mode);
    }


    private void createRoom() {
        error.setText("");

        String playerName = nameField.getText() == null ? "" : nameField.getText();
        if (playerName == null) return;

        int roomId = ThreadLocalRandom.current().nextInt(100_000, 999_999);
        roomIdField.setText(String.valueOf(roomId));

        String mode = rbGuess.isSelected() ? "GUESS_DRAWING" : "DEAF_PHONE";

        appData.isHost = true;
        connect(playerName, roomId, mode);
    }


    private void connect(String playerName, int roomId, String mode) {
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
                    mode
            );
            appData.clientConnection.send(join);

            enter.setDisable(true);
            create.setDisable(true);

        } catch (IOException e) {
            error.setText("Не вышло подключиться: " + e.getMessage());
        }
    }

    private void handleServerMessage(Message msg) {
        if (msg == null || msg.getType() == null) return;

        switch (msg.getType()) {

            case ERROR -> Platform.runLater(() -> {
                try {
                    Map<?, ?> err = gson.fromJson(msg.getPayload(), Map.class);
                    error.setText("ошибка: " + err);
                } catch (Exception ex) {
                    error.setText("ошибка: " + msg.getPayload());
                }
                enter.setDisable(false);
                create.setDisable(false);
            });

            case PLAYER_STATUS -> Platform.runLater(() -> {
                    guessScreen = new GuessDrawingScreen(appData);
                    stage.setScene(new Scene(guessScreen.getRoot(), 1000, 600));

            });

            default -> {
                // пока игнорируем
            }
        }
    }

    private int getRoomId() {
        try {
            return Integer.parseInt(roomIdField.getText().trim());
        } catch (Exception e) {
            error.setText("неверный номер комнаты");
            return -1;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
