package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;

import java.util.*;

public class GameState {
    private final int roomId;
    private GameMode mode;
    private final List<Player> players;
    private int round;
    private int timerSeconds;
    private final Map<Integer, List<ChainStep>> chains = new HashMap<>();

    public GameState(int roomId, GameMode mode) {
        this.roomId = roomId;
        this.mode = mode;
        this.players = new ArrayList<>();
        this.round = 1;
        this.timerSeconds = 0;
    }

    public int getRoomId() {
        return roomId;
    }

    public GameMode getMode() {
        return mode;
    }

    public void setMode(GameMode mode) {
        this.mode = mode;
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public void addPlayer(Player player) {
        if (!players.contains(player)) {
            players.add(player);
        }
    }

    public void removePlayer(Player player) {
        players.remove(player);
    }

    public int getRound() {
        return round;
    }

    public void nextRound() {
        round++;
    }

    public void resetRound() {
        round = 1;
    }

    public int getTimerSeconds() {
        return timerSeconds;
    }

    public void setTimerSeconds(int timerSeconds) {
        this.timerSeconds = timerSeconds;
    }

    public void decrementTimer() {
        if (timerSeconds > 0) {
            timerSeconds--;
        }
    }

    public Map<Integer, List<ChainStep>> getChains() {
        return chains;
    }

    public void clearChains() {
        chains.clear();
    }
}
