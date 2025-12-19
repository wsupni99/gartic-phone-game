package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.common.Message;
import ru.itis.garticphone.common.MessageType;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class GameServerJsonTest {

    @Test
    void toJsonAndParseMessageShouldBeConsistent() throws Exception {
        GameServer server = new GameServer();

        Message original = new Message(
                MessageType.CHAT,
                5,
                42,
                "Tester",
                "hello world"
        );

        Method toJson = GameServer.class.getDeclaredMethod("toJson", Message.class);
        toJson.setAccessible(true);
        String json = (String) toJson.invoke(server, original);

        Method parseMessage = GameServer.class.getDeclaredMethod("parseMessage", String.class);
        parseMessage.setAccessible(true);
        Message parsed = (Message) parseMessage.invoke(server, json);

        assertNotNull(parsed);
        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getRoomId(), parsed.getRoomId());
        assertEquals(original.getPlayerId(), parsed.getPlayerId());
        assertEquals(original.getPlayerName(), parsed.getPlayerName());
        assertEquals(original.getPayload(), parsed.getPayload());
    }

    @Test
    void parseMessageShouldReturnNullOnInvalidJson() throws Exception {
        GameServer server = new GameServer();
        Method parseMessage = GameServer.class
                .getDeclaredMethod("parseMessage", String.class);
        parseMessage.setAccessible(true);

        String invalid = "not a json";
        Message result = (Message) parseMessage.invoke(server, invalid);

        assertNull(result);
    }

    @Test
    void toJsonShouldEscapeQuotesAndBackslashes() throws Exception {
        GameServer server = new GameServer();
        Method toJson = GameServer.class
                .getDeclaredMethod("toJson", Message.class);
        toJson.setAccessible(true);
        Method parseMessage = GameServer.class
                .getDeclaredMethod("parseMessage", String.class);
        parseMessage.setAccessible(true);

        String trickyName = "Da\"nya\\Test";
        String trickyPayload = "{ \"k\":\"v\\\\\" }";

        Message original = new Message(
                MessageType.CHAT,
                1,
                10,
                trickyName,
                trickyPayload
        );

        String json = (String) toJson.invoke(server, original);
        Message parsed = (Message) parseMessage.invoke(server, json);

        assertEquals(trickyName, parsed.getPlayerName());
        assertEquals(trickyPayload, parsed.getPayload());
    }
}
