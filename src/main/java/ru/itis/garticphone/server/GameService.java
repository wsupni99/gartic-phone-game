package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;
import ru.itis.garticphone.client.PlayerState;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GameService {
    private final Map<Integer, GameState> rooms = new HashMap<>();
    private final Map<Integer, String> secretWords = new HashMap<>();
    private final List<String> words = new ArrayList<>();
    private final ScheduledExecutorService roundScheduler;

    public GameService(ScheduledExecutorService roundScheduler) {
        this.roundScheduler = roundScheduler;
        loadWords();
    }

    public void onConnect(Player player) {
        player.setState(PlayerState.CONNECTED);
    }

    public void onDisconnect(Player player) {
        handleLeave(player);
    }

    public void routeMessage(Player player, Message message) {
        if (player.isDisconnected()) {
            System.out.println("Ignoring message from disconnected player: " + player.getId());
            return;
        }

        if (message.getType() == null) {
            sendError(player, "400", "Message type is not set");
            return;
        }

        switch (message.getType()) {
            case JOIN:
                if (player.isInLobby() || player.isConnected()) {
                    handleJoin(player, message);
                } else {
                    sendError(player, "400", "Cannot join room from game state");
                }
                break;
            case LEAVE:
                handleLeave(player);
                break;
            case READY:
                if (player.isInLobby()) {
                    handleReady(player, message);
                } else {
                    sendError(player, "400", "READY is allowed only in lobby");
                }
                break;
            case START:
                if (player.isInLobby()) {
                    handleStart(player, message);
                } else {
                    sendError(player, "400", "START is allowed only from lobby");
                }
                break;
            case CHAT:
                if (player.isInLobby() || player.isInGame()) {
                    handleChat(player, message);
                } else {
                    sendError(player, "400", "Chat is not available in this state");
                }
                break;
            case DRAW:
            case GUESS:
            case TEXT_SUBMIT:
                if (player.isInGame()) {
                    switch (message.getType()) {
                        case DRAW -> handleDraw(player, message);
                        case GUESS -> handleGuess(player, message);
                        case TEXT_SUBMIT -> handleTextSubmit(player, message);
                        default -> {
                        }
                    }
                } else {
                    sendError(player, "400", "Game actions are allowed only in game");
                }
                break;
            default:
                sendError(player, "400", "Unknown message type: " + message.getType());
                break;
        }
    }

    private void handleJoin(Player player, Message message) {
        int roomId = message.getRoomId();
        String name = message.getPlayerName();

        if (name != null && !name.isBlank()) {
            player.setName(name);
        }

        GameState gameState;
        synchronized (rooms) {
            gameState = rooms.get(roomId);
            if (gameState == null) {
                gameState = new GameState(roomId);
                rooms.put(roomId, gameState);
                gameState.setHost(player.getId());
            }
            gameState.addPlayer(player);
        }

        player.setState(PlayerState.IN_LOBBY);
        broadcastPlayersUpdate(gameState);
    }

    public void handleLeave(Player player) {
        player.setState(PlayerState.DISCONNECTED);
        synchronized (rooms) {
            for (GameState room : rooms.values()) {
                if (room.getPlayers().contains(player)) {
                    room.removePlayer(player);
                    broadcastPlayersUpdate(room);
                }
            }
        }
    }

    private void broadcastPlayersUpdate(GameState room) {
        StringBuilder sb = new StringBuilder();
        for (Player p : room.getPlayers()) {
            if (sb.length() > 0) sb.append(";");
            boolean ready = room.getReadyPlayers().contains(p.getId());
            sb.append(p.getName()).append("=").append(ready)
                    .append(";score=").append(p.getScore());  // ← добавить
        }
        Message msg = new Message(
                MessageType.PLAYER_STATUS,
                room.getRoomId(),
                0,
                "SERVER",
                sb.toString()
        );
        for (Player p : room.getPlayers()) {
            p.send(msg);
        }
    }

    private void handleChat(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Room not found");
            return;
        }

        String payload = message.getPayload() != null ? message.getPayload() : "";
        Message response = new Message(
                MessageType.CHAT,
                roomId,
                from.getId(),
                from.getName(),
                payload
        );
        for (Player player : room.getPlayers()) {
            player.send(response);
        }
    }

    private void handleDraw(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Room not found");
            return;
        }

        Message response = new Message(
                MessageType.DRAW,
                roomId,
                from.getId(),
                from.getName(),
                message.getPayload()
        );
        for (Player player : room.getPlayers()) {
            player.send(response);
        }
    }

    private void handleGuess(Player from, Message message) {
        int roomId = message.getRoomId();
        String guess = message.getPayload();
        String secret = secretWords.get(roomId);
        if (secret == null || guess == null || guess.isBlank()) {
            sendError(from, "400", "Empty guess or secret word is not set");
            return;
        }

        if (secret.equalsIgnoreCase(guess.trim())) {
            from.addScore(1);

            StringBuilder payload = new StringBuilder();
            payload.append("correctPlayer=").append(from.getName())
                    .append(";word=").append(secret)
                    .append(";score=1");

            Message correct = new Message(
                    MessageType.CORRECT,
                    roomId,
                    from.getId(),
                    from.getName(),
                    payload.toString()
            );

            GameState room = rooms.get(roomId);
            if (room == null) {
                return;
            }
            for (Player player : room.getPlayers()) {
                player.send(correct);
            }

            secretWords.remove(roomId);
            roundScheduler.schedule(() -> autoNextRound(roomId), 5, TimeUnit.SECONDS);
        }
    }

    private void autoNextRound(int roomId) {
        GameState room = rooms.get(roomId);
        if (room == null) return;

        String newWord = generateWord();
        secretWords.put(roomId, newWord);

        room.resetRound();

        String base = "roundDuration=" + room.getTimerSeconds()
                + ";totalPlayers=" + room.getPlayers().size()
                + ";stage=DRAW";

        for (Player p : room.getPlayers()) {
            StringBuilder personal = new StringBuilder(base);
            if (room.isHost(p.getId())) {
                personal.append(";word=").append(newWord);
            }

            Message nextStart = new Message(
                    MessageType.START,
                    roomId,
                    0,
                    "SERVER",
                    personal.toString()
            );
            p.send(nextStart);
            p.setState(PlayerState.IN_GAME);
        }
        scheduleRoundEnd(roomId, room.getTimerSeconds());
    }

    private void handleReady(Player player, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(player, "404", "Room not found");
            return;
        }
        room.toggleReady(player.getId());
        broadcastPlayersUpdate(room);
    }

    private void handleStart(Player player, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(player, "404", "Room not found");
            return;
        }

        if (!room.isHost(player.getId())) {
            sendError(player, "403", "Only host can start the game");
            return;
        }
        if (!room.allReady()) {
            sendError(player, "412", "Not enough ready players");
            return;
        }

        int roundDuration = 60;
        try {
            roundDuration = Integer.parseInt(message.getPayload());
        } catch (NumberFormatException ignored) {
        }

        room.setTimerSeconds(roundDuration);
        room.resetRound();

        String word = generateWord();
        secretWords.put(roomId, word);

        Player host = null;
        for (Player p : room.getPlayers()) {
            if (room.isHost(p.getId())) {
                host = p;
                break;
            }
        }
        String hostName = host == null ? "" : host.getName();

        String base = "roundDuration=" + roundDuration
                + ";totalPlayers=" + room.getPlayers().size()
                + ";stage=DRAW"
                + ";hostName=" + hostName;

        for (Player p : room.getPlayers()) {
            StringBuilder personal = new StringBuilder(base);
            if (room.isHost(p.getId())) {
                personal.append(";word=").append(secretWords.get(roomId));
            }

            Message start = new Message(
                    MessageType.START,
                    roomId,
                    player.getId(),
                    player.getName(),
                    personal.toString()
            );

            p.send(start);
            p.setState(PlayerState.IN_GAME);
        }

        scheduleRoundEnd(roomId, roundDuration);
    }

    private void scheduleRoundEnd(int roomId, int roundDuration) {
        roundScheduler.schedule(() -> endRound(roomId), roundDuration, TimeUnit.SECONDS);
    }

    private void endRound(int roomId) {
        GameState room;
        synchronized (rooms) {
            room = rooms.get(roomId);
        }
        if (room == null) {
            return;
        }

        String secret = secretWords.get(roomId);
        if (secret == null) {
            return;
        }
        String payload = "word=" + secret;
        Message end = new Message(
                MessageType.ROUND_UPDATE,
                roomId,
                0,
                "SERVER",
                payload
        );
        for (Player p : room.getPlayers()) {
            p.send(end);
        }
        roundScheduler.schedule(() -> autoNextRound(roomId), 5, TimeUnit.SECONDS);
    }

    public String generateWord() {
        return words.get((int) (Math.random() * words.size()));
    }

    private void handleTextSubmit(Player from, Message message) {
        int roomId = message.getRoomId();
        GameState room = rooms.get(roomId);
        if (room == null) {
            sendError(from, "404", "Room not found");
            return;
        }

        if (message.getPayload() == null || message.getPayload().isBlank()) {
            sendError(from, "400", "Text payload is empty");
            return;
        }

        List<Player> list = room.getPlayers();
        int index = list.indexOf(from);
        if (index == -1) {
            return;
        }
        int nextIndex = (index + 1) % list.size();
        Player next = list.get(nextIndex);

        StringBuilder payload = new StringBuilder();
        payload.append("content=").append(message.getPayload())
                .append(";contentType=TEXT")
                .append(";roundNumber=").append(room.getRound());

        Message update = new Message(
                MessageType.ROUND_UPDATE,
                roomId,
                from.getId(),
                from.getName(),
                payload.toString()
        );

        next.send(update);
    }

    private void sendError(Player player, String code, String message) {
        String payload = "code=" + code + ";message=" + message;
        Message error = new Message(
                MessageType.ERROR,
                0,
                0,
                "SERVER",
                payload
        );
        try {
            player.send(error);
        } catch (Exception ignored) {
        }
    }

    private void loadWords() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                GameServer.class.getResourceAsStream("/words.txt")
                        ),
                        StandardCharsets.UTF_8
                )
        )) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    words.add(trimmed);
                }
            }
        } catch (Exception e) {
            words.clear();
            words.addAll(Arrays.asList("кот", "дом", "дерево", "машина", "солнце"));
        }
    }
}
