package ru.itis.garticphone.client;

import ru.itis.garticphone.common.MessageConnection;
import ru.itis.garticphone.common.Message;
import java.io.IOException;
import java.net.Socket;

public class Player {
    private final int id;
    private String name;
    private final MessageConnection connection;
    private PlayerState state;


    public Player(int id, String name, Socket socket) throws IOException {
        this.id = id;
        this.name = name;
        this.state = PlayerState.CONNECTED;
        this.connection = new MessageConnection(socket);
    }

    public void send(Message message) {
        if (connection != null) {
            try {
                connection.send(message);
            } catch (IOException ignored) {}
        }
    }

    public Message receiveLine() throws IOException, ClassNotFoundException {
        return connection.receive();
    }

    public void close() throws IOException {
        connection.close();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setState(PlayerState state) {
        this.state = state;
    }

    public boolean isInLobby() {
        return state == PlayerState.IN_LOBBY;
    }

    public boolean isInGame() {
        return state == PlayerState.IN_GAME;
    }

    public boolean isDisconnected() {
        return state == PlayerState.DISCONNECTED;
    }

    public boolean isConnected() {
        return state == PlayerState.CONNECTED;
    }
}
