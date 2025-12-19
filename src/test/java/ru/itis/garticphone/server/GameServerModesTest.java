package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;
import ru.itis.garticphone.client.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GameServerModesTest {

    private static class TestPlayer extends Player {
        private final List<String> sent = new ArrayList<>();

        public TestPlayer(int id, String name) {
            super(id, name);
        }

        @Override
        public void sendLine(String line) {
            sent.add(line);
        }

        public List<String> getSent() {
            return sent;
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void endRoundShouldSendWordInGuessMode() throws Exception {
        GameServer server = new GameServer();

        Field roomsField = GameServer.class.getDeclaredField("rooms");
        roomsField.setAccessible(true);
        Map<Integer, GameState> rooms = (Map<Integer, GameState>) roomsField.get(server);

        Field secretWordsField = GameServer.class.getDeclaredField("secretWords");
        secretWordsField.setAccessible(true);
        Map<Integer, String> secretWords = (Map<Integer, String>) secretWordsField.get(server);

        GameState room = new GameState(1, GameMode.GUESS_DRAWING);
        TestPlayer p1 = new TestPlayer(1, "P1");
        room.addPlayer(p1);
        rooms.put(1, room);
        secretWords.put(1, "кот");

        Method endRound = GameServer.class.getDeclaredMethod("endRound", int.class);
        endRound.setAccessible(true);
        endRound.invoke(server, 1);

        assertEquals(1, p1.getSent().size());
        String msg = p1.getSent().get(0);
        assertTrue(msg.contains("\"type\":\"ROUND_UPDATE\""));
        assertTrue(msg.contains("кот"));
    }

    @Test
    void sendFinalChainsShouldBroadcastChains() throws Exception {
        GameServer server = new GameServer();

        GameState room = new GameState(1, GameMode.DEAF_PHONE);
        TestPlayer p1 = new TestPlayer(1, "P1");
        TestPlayer p2 = new TestPlayer(2, "P2");
        room.addPlayer(p1);
        room.addPlayer(p2);

        List<ChainStep> steps = new ArrayList<>();
        steps.add(new ChainStep("start text"));
        steps.add(new ChainStep("image-bytes".getBytes()));
        room.getChains().put(1, steps);

        Method sendFinalChains = GameServer.class.getDeclaredMethod("sendFinalChains", GameState.class);
        sendFinalChains.setAccessible(true);
        sendFinalChains.invoke(server, room);

        assertEquals(1, p1.getSent().size());
        assertEquals(1, p2.getSent().size());

        String msg1 = p1.getSent().get(0);
        // смотри на строку из дебага: contentType, TEXT, DRAW и текст
        assertTrue(msg1.contains("\\\"contentType\\\":\\\"FINAL_CHAIN\\\""));
        assertTrue(msg1.contains("\\\"type\\\":\\\"TEXT\\\""));
        assertTrue(msg1.contains("start text"));
        assertTrue(msg1.contains("\\\"type\\\":\\\"DRAW\\\""));

        String msg2 = p2.getSent().get(0);
        assertTrue(msg2.contains("\\\"contentType\\\":\\\"FINAL_CHAIN\\\""));
    }
}
