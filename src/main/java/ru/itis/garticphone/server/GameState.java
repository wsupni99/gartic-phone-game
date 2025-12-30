package ru.itis.garticphone.server;

import ru.itis.garticphone.client.Player;

import java.util.*;

public class GameState {
    private final int roomId;
    private final List<Player> players;
    private int round;
    private int timerSeconds;
    private final Set<Integer> readyPlayers = new HashSet<>();
    private final int minPlayers;
    private int hostId = -1;

    public GameState(int roomId) {
        this.roomId = roomId;
        this.minPlayers = 2;
        this.players = new ArrayList<>();
        this.round = 1;
        this.timerSeconds = 0;
    }

    public int getRoomId() {
        return roomId;
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

    public void resetRound() {
        round = 1;
    }

    public int getTimerSeconds() {
        return timerSeconds;
    }

    public void setTimerSeconds(int timerSeconds) {
        this.timerSeconds = timerSeconds;
    }

    public void setHost(int playerId) {
        this.hostId = playerId;
    }

    public int getHostId() {
        return hostId;
    }

    public boolean isHost(int playerId) {
        return playerId == hostId;
    }

    public void toggleReady(int playerId) {
        if (readyPlayers.contains(playerId)) readyPlayers.remove(playerId);
        else readyPlayers.add(playerId);
    }

    public boolean allReady() {
        return players.size() >= minPlayers &&
                readyPlayers.size() == players.size();
    }

    public Set<Integer> getReadyPlayers() {
        return readyPlayers;
    }
}
