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
<<<<<<< Updated upstream
=======
    private final Runnable onExitToLogin; // колбэк в LoginScreen
    private final Gson gson = new Gson();
>>>>>>> Stashed changes

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

<<<<<<< Updated upstream
=======
    // chat
>>>>>>> Stashed changes
    private final TextArea chatArea = new TextArea();
    private final TextField chatField = new TextField();
    private final Button chatSendBtn = new Button("Отправить");

    private final Label statusLbl = new Label("Статус: ...");

    private boolean inGame = false;
    private boolean iAmReady = false;
    private boolean startRequested = false; // чтобы не слать START много раз

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
<<<<<<< Updated upstream
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
=======
                // payload: Map name -> ready [file:82]
                int total = 0;
                int ready = 0;
                boolean allReady = false;

                try {
                    Map<?, ?> m = gson.fromJson(msg.getPayload(), Map.class);
                    total = m.size();
                    for (Object v : m.values()) {
                        if (Boolean.TRUE.equals(v)) ready++;
                    }
                    allReady = (total > 0 && ready == total);
                } catch (Exception ignored) { }
>>>>>>> Stashed changes

                readyLbl.setText("Готовы: " + ready + "/" + total);

                // Авто-старт: когда все готовы, host сам отправляет START (кнопки Start нет) [file:82]
                if (appData.isHost && !inGame && allReady && !startRequested) {
                    startRequested = true;
                    statusLbl.setText("Статус: Все готовы, запускаем...");
                    sendStartAuto();
                }
            });

            case START -> Platform.runLater(() -> {
                inGame = true;
                statusLbl.setText("Статус: Игра началась");
                clearCanvas();

                readyBtn.setDisable(true);
                durationField.setDisable(true);
                leaveBtn.setDisable(false);

<<<<<<< Updated upstream
                canvas.setDisable(!appData.isHost);
                clearBtn.setDisable(!appData.isHost);

                String maybeWord = getField(msg.getPayload(), "word");
=======
                // рисовать может только ведущий (сервер тоже это проверяет) [file:82]
                canvas.setDisable(!appData.isHost);
                clearBtn.setDisable(!appData.isHost);

                // слово — только если сервер прислал его в START ведущему [file:82]
                String maybeWord = readStringFromJson(msg.getPayload(), "word");
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream

                if (ignoreMyEcho
                        && appData.playerName != null
                        && appData.playerName.equals(msg.getPlayerName())) {
                    return;
                }
=======
                if (ignoreMyEcho && appData.playerName != null && appData.playerName.equals(msg.getPlayerName())) return;
>>>>>>> Stashed changes

                String payload = msg.getPayload();
                if (payload == null || payload.isBlank()) return;

                String[] parts = payload.split(";");
                if (parts.length != 4) return;

                try {
<<<<<<< Updated upstream
                    double x1 = Double.parseDouble(parts[0]);
                    double y1 = Double.parseDouble(parts[1]);
                    double x2 = Double.parseDouble(parts[2]);
                    double y2 = Double.parseDouble(parts[3]);

                    Platform.runLater(() -> gc.strokeLine(x1, y1, x2, y2));
                } catch (NumberFormatException ignored) {
                }
=======
                    dto = gson.fromJson(msg.getPayload(), StrokeDto.class);
                } catch (Exception e) {
                    return;
                }
                Platform.runLater(() -> gc.strokeLine(dto.x1, dto.y1, dto.x2, dto.y2));
>>>>>>> Stashed changes
            }

            case CHAT -> Platform.runLater(() ->
                    chatArea.appendText(msg.getPlayerName() + ": " + msg.getPayload() + "\n")
            );

            case CORRECT -> Platform.runLater(() -> {
                stopTimer();
                chatArea.appendText("SERVER: " + msg.getPayload() + "\n");
                statusLbl.setText("Статус: Правильно! Следующий раунд скоро...");
            });

            case ROUND_UPDATE -> Platform.runLater(() -> {
                stopTimer();
                chatArea.appendText("SERVER: " + msg.getPayload() + "\n");
                statusLbl.setText("Статус: Раунд завершён");
            });

            case ERROR -> Platform.runLater(() -> {
<<<<<<< Updated upstream
                String text = getField(msg.getPayload(), "message");
                if (text == null) {
                    text = msg.getPayload();
                }
                statusLbl.setText("Статус: ERROR " + text);
                chatArea.appendText("SERVER: ERROR " + text + "\n");
=======
                statusLbl.setText("Статус: ERROR " + msg.getPayload());
                chatArea.appendText("SERVER: ERROR " + msg.getPayload() + "\n");
                // если автостарт не прошёл (например, сервер ответил 412/403) — разрешим повтор
                startRequested = false;
>>>>>>> Stashed changes
            });

            default -> {
            }
        }
    }

    private void buildUi() {
        root.setPadding(new Insets(10));

        roomCodeLbl.setText("Комната: " + (appData.roomId == null ? "-" : appData.roomId));
        roleLbl.setText(appData.isHost ? "Роль: ведущий" : "Роль: игрок");

        wordLbl.setText(appData.isHost ? "Слово: (ждём старт)" : "");
        wordLbl.setVisible(appData.isHost);
        wordLbl.setManaged(appData.isHost);
        wordLbl.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;"); // слово крупнее

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

<<<<<<< Updated upstream
        startBtn.setDisable(!appData.isHost);
=======
        leaveBtn.setDisable(false);
>>>>>>> Stashed changes

        statusLbl.setText("Статус: Лобби. Все нажимают Ready — игра стартует автоматически.");
    }

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

        // UI переключаем сразу
        if (onExitToLogin != null) onExitToLogin.run();

        // сеть закрываем в фоне
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

            // если host снял ready — позволим автостарту снова
            if (appData.isHost && !iAmReady) startRequested = false;

        } catch (IOException e) {
            statusLbl.setText("Статус: Ошибка отправки READY");
        }
    }

    // Авто START (только host). Сервер разрешает START только из лобби и только host. [file:82]
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

<<<<<<< Updated upstream
=======
    // CHAT всегда; GUESS только когда игра идёт и ты не ведущий [file:82]
>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
            String payload = lastX + ";" + lastY + ";" + x + ";" + y;
=======
            StrokeDto dto = new StrokeDto(lastX, lastY, x, y);
            String payload = gson.toJson(dto);
>>>>>>> Stashed changes

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
