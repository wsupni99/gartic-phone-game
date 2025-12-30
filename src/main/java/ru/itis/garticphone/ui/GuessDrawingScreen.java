package ru.itis.garticphone.ui;

import com.google.gson.Gson;
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
import java.util.Map;

public class GuessDrawingScreen {

    private final AppData appData;
    private final Gson gson = new Gson();

    // UI root
    private final BorderPane root = new BorderPane();

    // top info
    private final Label roomCodeLbl = new Label();
    private final Label roleLbl = new Label();
    private final Label readyLbl = new Label("Готовы: 0/0");
    private final Label wordLbl = new Label();
    private final Label timerLbl = new Label("Время: --");

    private final Button readyBtn = new Button("Ready");
    private final Button startBtn = new Button("Start");
    private final TextField durationField = new TextField("60");

    // canvas
    private final Canvas canvas = new Canvas(650, 450);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();
    private final Button clearBtn = new Button("Очистить");

    // chat (и догадки тоже здесь)
    private final TextArea chatArea = new TextArea();
    private final TextField chatField = new TextField();
    private final Button chatSendBtn = new Button("Отправить");

    // bottom status
    private final Label statusLbl = new Label("Статус: ...");

    // state
    private boolean inGame = false;
    private boolean iAmReady = false;

    // drawing state
    private double lastX, lastY;

    // timer
    private Timeline timeline;
    private int secondsLeft = 0;

    // чтобы не рисовать "эхо" (сервер может вернуть твой DRAW всем, включая тебя)
    private final boolean ignoreMyEcho = true;

    // payload для DRAW (отрезок линии)
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

    // вызывать из startListening(...) для каждого входящего Message
    public void handleIncoming(Message msg) {
        if (msg == null || msg.getType() == null) return;

        switch (msg.getType()) {

            case PLAYER_STATUS -> Platform.runLater(() -> {
                // payload: Map<String, Boolean> name -> ready
                int total = 0;
                int ready = 0;
                try {
                    Map<?, ?> m = gson.fromJson(msg.getPayload(), Map.class);
                    total = m.size();
                    for (Object v : m.values()) {
                        if (Boolean.TRUE.equals(v)) ready++;
                    }
                } catch (Exception ignored) { }

                readyLbl.setText("Готовы: " + ready + "/" + total);
            });

            case START -> Platform.runLater(() -> {
                inGame = true;
                statusLbl.setText("Статус: Игра началась");
                clearCanvas();

                // кнопки лобби
                readyBtn.setDisable(true);
                startBtn.setDisable(true);
                durationField.setDisable(true);

                // холст активен только у ведущего
                canvas.setDisable(!appData.isHost);
                clearBtn.setDisable(!appData.isHost);

                // слово — только если сервер прислал в START
                String maybeWord = readStringFromJson(msg.getPayload(), "word");
                if (appData.isHost && maybeWord != null && !maybeWord.isBlank()) {
                    wordLbl.setText("Слово: " + maybeWord);
                } else {
                    wordLbl.setText(appData.isHost ? "Слово: (не пришло)" : "");
                }

                Integer roundDuration = readIntFromJson(msg.getPayload(), "roundDuration");
                if (roundDuration != null) startTimer(roundDuration);
            });

            case DRAW -> {
                // рисуем только когда игра стартовала (иначе это просто мусор)
                if (!inGame) return;

                if (ignoreMyEcho && appData.playerName != null && appData.playerName.equals(msg.getPlayerName())) {
                    return;
                }

                StrokeDto dto;
                try {
                    dto = gson.fromJson(msg.getPayload(), StrokeDto.class);
                } catch (Exception e) {
                    return;
                }

                Platform.runLater(() -> gc.strokeLine(dto.x1, dto.y1, dto.x2, dto.y2));
            }

            case CHAT -> Platform.runLater(() ->
                    chatArea.appendText(msg.getPlayerName() + ": " + msg.getPayload() + "\n")
            );

            case CORRECT -> Platform.runLater(() -> {
                stopTimer();
                chatArea.appendText("SERVER: " + msg.getPayload() + "\n");
                statusLbl.setText("Статус: Кто-то угадал");
            });

            case ROUND_UPDATE -> Platform.runLater(() -> {
                stopTimer();
                chatArea.appendText("SERVER: " + msg.getPayload() + "\n");
                statusLbl.setText("Статус: Раунд завершён");
                // после конца раунда можно оставить inGame=true, т.к. сервер может сам начать новый раунд
            });

            case ERROR -> Platform.runLater(() -> {
                statusLbl.setText("Статус: ERROR " + msg.getPayload());
                chatArea.appendText("SERVER: ERROR " + msg.getPayload() + "\n");
            });

            default -> { }
        }
    }

    // ---------------- UI ----------------

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

        // center
        root.setCenter(new StackPane(canvas));

        // right chat
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

        // bottom
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
        // Пока не пришёл START — игра не началась, рисовать нельзя вообще
        inGame = false;
        canvas.setDisable(true);
        clearBtn.setDisable(true);

        // Ведущий может жать Start, остальные — нет
        startBtn.setDisable(!appData.isHost);

        statusLbl.setText("Статус: Лобби. Нажмите Ready, затем ведущий нажмёт Start.");
    }

    // ---------------- Buttons ----------------

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
        // READY разрешён только в лобби, но даже если нажмут поздно — сервер вернёт ERROR [file:79]
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

    // Одна строка ввода:
    // - всегда отправляем CHAT (можно и в лобби, и в игре) [file:79]
    // - если игра идёт и ты НЕ ведущий — то же самое отправляем как GUESS [file:79]
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

    // ---------------- Drawing ----------------

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

            // локально
            gc.strokeLine(lastX, lastY, x, y);

            // всем
            StrokeDto dto = new StrokeDto(lastX, lastY, x, y);
            String payload = gson.toJson(dto);

            try {
                appData.clientConnection.send(new Message(
                        MessageType.DRAW,
                        appData.roomId == null ? 0 : appData.roomId,
                        0,
                        appData.playerName,
                        payload
                ));
            } catch (IOException ignored) { }

            lastX = x;
            lastY = y;
        });
    }

    // ---------------- Timer ----------------

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

    // ---------------- tiny json helpers ----------------

    private Integer readIntFromJson(String json, String key) {
        try {
            Map<?, ?> map = gson.fromJson(json, Map.class);
            Object v = map.get(key);
            if (v instanceof Number n) return n.intValue();
        } catch (Exception ignored) { }
        return null;
    }

    private String readStringFromJson(String json, String key) {
        try {
            Map<?, ?> map = gson.fromJson(json, Map.class);
            Object v = map.get(key);
            if (v != null) return String.valueOf(v);
        } catch (Exception ignored) { }
        return null;
    }
}
