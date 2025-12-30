package ru.itis.garticphone.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.IOException;

public class GuessDrawingScreen {

    private final AppData appData;

    private final BorderPane root = new BorderPane();

    private final Label roomCodeLbl = new Label();
    private final Label roleLbl = new Label();
    private final Label readyLbl = new Label("Готовы: 0/0");
    private final Label wordLbl = new Label();
    private final Label timerLbl = new Label("Время: --");

    private final Button readyBtn = new Button("Ready");
    private final Button startBtn = new Button("Start");
    private final TextField durationField = new TextField("60");

    private final Canvas canvas = new Canvas(650, 450);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();
    private final Button clearBtn = new Button("Очистить");

    private final TextArea chatArea = new TextArea();
    private final TextField chatField = new TextField();
    private final Button chatSendBtn = new Button("Отправить");

    private final Label statusLbl = new Label("Статус: ...");

    private boolean inGame = false;
    private boolean iAmReady = false;

    private double lastX, lastY;

    private Timeline timeline;
    private int secondsLeft = 0;

    private final boolean ignoreMyEcho = true;

    private static class StrokeDto {
        double x1, y1, x2, y2;
        StrokeDto(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
    }

    public GuessDrawingScreen(AppData appData) {
        this.appData = appData;

        buildUi();
        setupBrush();
        applyInitialRules();
        wireButtons();
        wireDrawing();
    }

    public Parent getRoot() {
        return root;
    }

    public void handleIncoming(Message msg) {
        if (msg == null || msg.getType() == null) return;

        switch (msg.getType()) {

            case PLAYER_STATUS -> Platform.runLater(() -> {
                int total = 0;
                int ready = 0;

                String payload = msg.getPayload();
                if (payload != null && !payload.isBlank()) {
                    String[] parts = payload.split(";");
                    total = parts.length;
                    for (String part : parts) {
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2 && "true".equalsIgnoreCase(kv[1].trim())) {
                            ready++;
                        }
                    }
                }

                readyLbl.setText("Готовы: " + ready + "/" + total);
            });

            case START -> Platform.runLater(() -> {
                inGame = true;
                statusLbl.setText("Статус: Игра началась");
                clearCanvas();

                readyBtn.setDisable(true);
                startBtn.setDisable(true);
                durationField.setDisable(true);

                canvas.setDisable(!appData.isHost);
                clearBtn.setDisable(!appData.isHost);

                String maybeWord = getField(msg.getPayload(), "word");
                if (appData.isHost && maybeWord != null && !maybeWord.isBlank()) {
                    wordLbl.setText("Слово: " + maybeWord);
                } else {
                    wordLbl.setText(appData.isHost ? "Слово: (не пришло)" : "");
                }

                Integer roundDuration = getIntField(msg.getPayload(), "roundDuration");
                if (roundDuration != null) {
                    startTimer(roundDuration);
                }
            });

            case DRAW -> {
                if (!inGame) return;

                if (ignoreMyEcho
                        && appData.playerName != null
                        && appData.playerName.equals(msg.getPlayerName())) {
                    return;
                }

                String payload = msg.getPayload();
                if (payload == null || payload.isBlank()) return;

                String[] parts = payload.split(";");
                if (parts.length != 4) return;

                try {
                    double x1 = Double.parseDouble(parts[0]);
                    double y1 = Double.parseDouble(parts[1]);
                    double x2 = Double.parseDouble(parts[2]);
                    double y2 = Double.parseDouble(parts[3]);

                    Platform.runLater(() -> gc.strokeLine(x1, y1, x2, y2));
                } catch (NumberFormatException ignored) {
                }
            }

            case CHAT -> Platform.runLater(() ->
                    chatArea.appendText(msg.getPlayerName() + ": " + msg.getPayload() + "\n")
            );

            case CORRECT -> Platform.runLater(() -> {
                stopTimer();
                chatArea.appendText("SERVER: " + msg.getPayload() + "\n");
                statusLbl.setText("Статус: Правильно! Следующий раунд начнётся через 5 сек...");
            });

            case ROUND_UPDATE -> Platform.runLater(() -> {
                stopTimer();
                chatArea.appendText("SERVER: " + msg.getPayload() + "\n");
                statusLbl.setText("Статус: Раунд завершён");
            });

            case ERROR -> Platform.runLater(() -> {
                String text = getField(msg.getPayload(), "message");
                if (text == null) {
                    text = msg.getPayload();
                }
                statusLbl.setText("Статус: ERROR " + text);
                chatArea.appendText("SERVER: ERROR " + text + "\n");
            });

            default -> {
            }
        }
    }

    private void buildUi() {
        root.setPadding(new Insets(10));

        roomCodeLbl.setText("Комната: " + (appData.roomId == null ? "-" : appData.roomId));
        roleLbl.setText(appData.isHost ? "Роль: ведущий" : "Роль: игрок");
        wordLbl.setText(appData.isHost ? "Слово: (ждём START)" : "");
        wordLbl.setVisible(appData.isHost);
        wordLbl.setManaged(appData.isHost);

        durationField.setPrefWidth(60);

        HBox top = new HBox(12,
                roomCodeLbl,
                roleLbl,
                readyLbl,
                new Label("Сек:"), durationField,
                readyBtn,
                startBtn,
                wordLbl,
                timerLbl
        );
        top.setPadding(new Insets(10));
        root.setTop(top);

        root.setCenter(new StackPane(canvas));

        chatArea.setEditable(false);
        chatArea.setPrefWidth(260);
        chatField.setPromptText("Чат / догадка");

        VBox chatBox = new VBox(8,
                new Label("Чат"),
                chatArea,
                new HBox(8, chatField, chatSendBtn)
        );
        chatBox.setPadding(new Insets(10));
        VBox.setVgrow(chatArea, Priority.ALWAYS);
        root.setRight(chatBox);

        HBox bottomControls = new HBox(10, clearBtn);
        VBox bottom = new VBox(8, bottomControls, statusLbl);
        bottom.setPadding(new Insets(10));
        root.setBottom(bottom);
    }

    private void setupBrush() {
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(3);
    }

    private void applyInitialRules() {
        inGame = false;
        canvas.setDisable(true);
        clearBtn.setDisable(true);

        startBtn.setDisable(!appData.isHost);

        statusLbl.setText("Статус: Лобби. Нажмите Ready, затем ведущий нажмёт Start.");
    }

    private void wireButtons() {
        readyBtn.setOnAction(e -> toggleReady());
        startBtn.setOnAction(e -> sendStart());

        clearBtn.setOnAction(e -> {
            if (appData.isHost && inGame) clearCanvas();
        });

        chatSendBtn.setOnAction(e -> sendChatOrGuess());
        chatField.setOnAction(e -> sendChatOrGuess());
    }

    private void toggleReady() {
        try {
            appData.clientConnection.send(new Message(
                    MessageType.READY,
                    appData.roomId == null ? 0 : appData.roomId,
                    0,
                    appData.playerName,
                    ""
            ));
            iAmReady = !iAmReady;
            readyBtn.setText(iAmReady ? "Unready" : "Ready");
        } catch (IOException e) {
            statusLbl.setText("Статус: Ошибка отправки READY");
        }
    }

    private void sendStart() {
        if (!appData.isHost) return;

        String payload = durationField.getText() == null ? "60" : durationField.getText().trim();
        if (payload.isBlank()) payload = "60";

        try {
            appData.clientConnection.send(new Message(
                    MessageType.START,
                    appData.roomId == null ? 0 : appData.roomId,
                    0,
                    appData.playerName,
                    payload
            ));
        } catch (IOException e) {
            statusLbl.setText("Статус: Ошибка отправки START");
        }
    }

    private void sendChatOrGuess() {
        String text = chatField.getText() == null ? "" : chatField.getText().trim();
        if (text.isEmpty()) return;
        chatField.clear();

        try {
            appData.clientConnection.send(new Message(
                    MessageType.CHAT,
                    appData.roomId == null ? 0 : appData.roomId,
                    0,
                    appData.playerName,
                    text
            ));
        } catch (IOException e) {
            statusLbl.setText("Статус: Ошибка отправки CHAT");
        }

        if (inGame && !appData.isHost) {
            try {
                appData.clientConnection.send(new Message(
                        MessageType.GUESS,
                        appData.roomId == null ? 0 : appData.roomId,
                        0,
                        appData.playerName,
                        text
                ));
            } catch (IOException e) {
                statusLbl.setText("Статус: Ошибка отправки GUESS");
            }
        }
    }

    private void wireDrawing() {
        canvas.setOnMousePressed(e -> {
            if (!inGame || !appData.isHost) return;
            lastX = e.getX();
            lastY = e.getY();
        });

        canvas.setOnMouseDragged(e -> {
            if (!inGame || !appData.isHost) return;

            double x = e.getX();
            double y = e.getY();

            gc.strokeLine(lastX, lastY, x, y);

            String payload = lastX + ";" + lastY + ";" + x + ";" + y;

            try {
                appData.clientConnection.send(new Message(
                        MessageType.DRAW,
                        appData.roomId == null ? 0 : appData.roomId,
                        0,
                        appData.playerName,
                        payload
                ));
            } catch (IOException ignored) {
            }

            lastX = x;
            lastY = y;
        });
    }

    private void startTimer(int seconds) {
        stopTimer();
        secondsLeft = Math.max(0, seconds);
        timerLbl.setText("Время: " + secondsLeft);

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            secondsLeft--;
            if (secondsLeft < 0) {
                stopTimer();
                return;
            }
            timerLbl.setText("Время: " + secondsLeft);
        }));
        timeline.setCycleCount(secondsLeft + 1);
        timeline.playFromStart();
    }

    private void stopTimer() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    private void clearCanvas() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private String getField(String payload, String key) {
        if (payload == null) return null;
        String[] parts = payload.split(";");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }

    private Integer getIntField(String payload, String key) {
        String v = getField(payload, key);
        if (v == null) return null;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
