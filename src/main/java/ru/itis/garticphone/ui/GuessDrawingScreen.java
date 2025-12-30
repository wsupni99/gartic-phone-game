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
import java.util.HashMap;
import java.util.Map;

public class GuessDrawingScreen {

    private final AppData appData;
    private final Runnable onExitToLogin;

    // UI
    private final BorderPane root = new BorderPane();

    private final Label roomCodeLbl = new Label();
    private final Label roleLbl = new Label();
    private final Label readyLbl = new Label("Готовы: 0/0");
    private final Label wordLbl = new Label();
    private final Label timerLbl = new Label("Время: --");

    private final Button readyBtn = new Button("Ready");
    private final TextField durationField = new TextField("60"); // только host
    private final Button leaveBtn = new Button("Выйти");

    private final Canvas canvas = new Canvas(650, 450);
    private final GraphicsContext gc = canvas.getGraphicsContext2D();
    private final Button clearBtn = new Button("Очистить");

    private final TextArea chatArea = new TextArea();
    private final TextField chatField = new TextField();
    private final Button chatSendBtn = new Button("Отправить");

    private final Label statusLbl = new Label("Статус: ...");

    // state
    private boolean inGame = false;
    private boolean iAmReady = false;
    private boolean startRequested = false;

    // drawing
    private double lastX, lastY;

    // timer
    private Timeline timeline;
    private int secondsLeft = 0;

    private final boolean ignoreMyEcho = true;

    public GuessDrawingScreen(AppData appData, Runnable onExitToLogin) {
        this.appData = appData;
        this.onExitToLogin = onExitToLogin;

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
                // payload: "name=true;name2=false;..." [server] [file:96]
                int total = 0;
                int ready = 0;
                boolean allReady = false;

                String payload = msg.getPayload();
                if (payload != null && !payload.isBlank()) {
                    String[] parts = payload.split(";");
                    for (String part : parts) {
                        if (part == null || part.isBlank()) continue;
                        total++;
                        String[] kv = part.split("=", 2);
                        if (kv.length == 2 && "true".equalsIgnoreCase(kv[1].trim())) {
                            ready++;
                        }
                    }
                    allReady = (total > 0 && ready == total);
                }

                readyLbl.setText("Готовы: " + ready + "/" + total);

                // если кто-то снял ready — разрешим повтор автостарта
                if (!allReady) startRequested = false;

                // авто-старт: host отправляет START, когда все готовы [file:96]
                if (appData.isHost && !inGame && allReady && !startRequested) {
                    startRequested = true;
                    statusLbl.setText("Статус: Все готовы, запускаем...");
                    sendStartAuto();
                }
            });

            case START -> Platform.runLater(() -> {
                // payload: "roundDuration=...;totalPlayers=...;stage=...;hostName=...;word=..." [file:96]
                inGame = true;
                statusLbl.setText("Статус: Игра началась");
                clearCanvas();

                readyBtn.setDisable(true);
                durationField.setDisable(true);

                canvas.setDisable(!appData.isHost);
                clearBtn.setDisable(!appData.isHost);

                Map<String, String> data = parseKvPayload(msg.getPayload());

                String maybeWord = data.get("word");
                if (appData.isHost && maybeWord != null && !maybeWord.isBlank()) {
                    wordLbl.setText("Слово: " + maybeWord);
                } else {
                    wordLbl.setText(appData.isHost ? "Слово: (не пришло)" : "");
                }

                Integer roundDuration = parseIntSafe(data.get("roundDuration"));
                if (roundDuration != null) startTimer(roundDuration);
            });

            case DRAW -> {
                if (!inGame) return;

                if (ignoreMyEcho
                        && appData.playerName != null
                        && appData.playerName.equals(msg.getPlayerName())) {
                    return;
                }

                // payload: "x1;y1;x2;y2"
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
                } catch (NumberFormatException ignored) { }
            }

            case CHAT -> Platform.runLater(() ->
                    chatArea.appendText(msg.getPlayerName() + ": " + msg.getPayload() + "\n")
            );

            case CORRECT -> Platform.runLater(() -> {
                stopTimer();
                // payload: "correctPlayer=...;word=...;score=1" [file:96]
                Map<String, String> data = parseKvPayload(msg.getPayload());
                String cp = data.getOrDefault("correctPlayer", "кто-то");
                String w = data.getOrDefault("word", "?");
                chatArea.appendText("SERVER: Угадали! " + cp + " (" + w + ")\n");
                statusLbl.setText("Статус: Правильно! Следующий раунд скоро...");
            });

            case ROUND_UPDATE -> Platform.runLater(() -> {
                stopTimer();
                // payload: "word=..." [file:96]
                Map<String, String> data = parseKvPayload(msg.getPayload());
                if (data.containsKey("word")) {
                    chatArea.appendText("SERVER: Раунд завершён. Слово было: " + data.get("word") + "\n");
                } else {
                    chatArea.appendText("SERVER: " + msg.getPayload() + "\n");
                }
                statusLbl.setText("Статус: Раунд завершён");
            });

            case ERROR -> Platform.runLater(() -> {
                // payload: "code=...;message=..." [file:96]
                Map<String, String> data = parseKvPayload(msg.getPayload());
                String text = data.getOrDefault("message", msg.getPayload());
                statusLbl.setText("Статус: ERROR " + text);
                chatArea.appendText("SERVER: ERROR " + text + "\n");
                startRequested = false;
            });

            default -> { }
        }
    }

    // ---------------- UI ----------------

    private void buildUi() {
        root.setPadding(new Insets(10));

        roomCodeLbl.setText("Комната: " + (appData.roomId == null ? "-" : appData.roomId));
        roleLbl.setText(appData.isHost ? "Роль: ведущий" : "Роль: игрок");

        wordLbl.setText(appData.isHost ? "Слово: (ждём старт)" : "");
        wordLbl.setVisible(appData.isHost);
        wordLbl.setManaged(appData.isHost);
        wordLbl.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;"); // крупнее слово

        durationField.setPrefWidth(60);
        durationField.setDisable(!appData.isHost);
        durationField.setVisible(appData.isHost);
        durationField.setManaged(appData.isHost);

        Label secLbl = new Label("Сек:");
        secLbl.setVisible(appData.isHost);
        secLbl.setManaged(appData.isHost);

        HBox top = new HBox(12,
                roomCodeLbl,
                roleLbl,
                readyLbl,
                secLbl, durationField,
                readyBtn,
                leaveBtn,
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
        startRequested = false;

        canvas.setDisable(true);
        clearBtn.setDisable(true);
        leaveBtn.setDisable(false);

        statusLbl.setText("Статус: Лобби. Все нажимают Ready — игра стартует автоматически.");
    }

    // ---------------- Buttons ----------------

    private void wireButtons() {
        readyBtn.setOnAction(e -> toggleReady());
        leaveBtn.setOnAction(e -> exitToLogin());

        clearBtn.setOnAction(e -> {
            if (appData.isHost && inGame) clearCanvas();
        });

        chatSendBtn.setOnAction(e -> sendChatOrGuess());
        chatField.setOnAction(e -> sendChatOrGuess());
    }

    private void exitToLogin() {
        stopTimer();

        // UI сразу назад
        if (onExitToLogin != null) onExitToLogin.run();

        // сеть закрываем в фоне (чтобы UI не зависал)
        new Thread(() -> {
            try {
                appData.clientConnection.send(new Message(
                        MessageType.LEAVE,
                        appData.roomId == null ? 0 : appData.roomId,
                        0,
                        appData.playerName,
                        ""
                ));
            } catch (Exception ignored) { }

            try {
                if (appData.clientConnection != null) appData.clientConnection.close();
            } catch (Exception ignored) { }
        }, "disconnect-thread").start();
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

            if (appData.isHost && !iAmReady) startRequested = false;

        } catch (IOException e) {
            statusLbl.setText("Статус: Ошибка отправки READY");
        }
    }

    private void sendStartAuto() {
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
            statusLbl.setText("Статус: Ошибка авто-старта");
            startRequested = false;
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

    // ---------------- helpers ----------------

    private static Integer parseIntSafe(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // Парсер для payload вида "k=v;k2=v2" (для START/ERROR/CORRECT/ROUND_UPDATE) [file:96]
    private static Map<String, String> parseKvPayload(String payload) {
        Map<String, String> map = new HashMap<>();
        if (payload == null || payload.isBlank()) return map;

        String[] parts = payload.split(";");
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1]);
            }
        }
        return map;
    }
}
