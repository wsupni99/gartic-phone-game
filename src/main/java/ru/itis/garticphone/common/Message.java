package ru.itis.garticphone.common;

public class Message {

    private MessageType type;

    private int roomId;

    private int playerId;

    private String playerName;

    private String payload;

    public Message() {
    }

    public Message(MessageType type, int roomId, int playerId, String playerName, String payload) {
        this.type = type;
        this.roomId = roomId;
        this.playerId = playerId;
        this.playerName = playerName;
        this.payload = payload;
    }

    public static String toJson(Message message) {
        if (message == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        sb.append("\"type\":\"")
                .append(message.type != null ? message.type.name() : "")
                .append("\",");

        sb.append("\"roomId\":")
                .append(message.roomId)
                .append(",");

        sb.append("\"playerId\":")
                .append(message.playerId)
                .append(",");

        sb.append("\"playerName\":");
        if (message.playerName == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(escape(message.playerName)).append("\"");
        }
        sb.append(",");

        sb.append("\"payload\":");
        if (message.payload == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(escape(message.payload)).append("\"");
        }

        sb.append("}");
        return sb.toString();
    }

    public static Message parse(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }

        Message result = new Message();
        try {
            String body = trimmed.substring(1, trimmed.length() - 1);

            String typeValue = extractStringField(body, "type");
            if (typeValue != null && !typeValue.isBlank()) {
                try {
                    result.type = MessageType.valueOf(typeValue);
                } catch (IllegalArgumentException ignored) {
                    return null;
                }
            }

            String roomIdValue = extractNumberField(body, "roomId");
            if (roomIdValue != null && !roomIdValue.isBlank()) {
                result.roomId = Integer.parseInt(roomIdValue);
            }

            String playerIdValue = extractNumberField(body, "playerId");
            if (playerIdValue != null && !playerIdValue.isBlank()) {
                result.playerId = Integer.parseInt(playerIdValue);
            }

            String playerNameValue = extractNullableStringField(body, "playerName");
            result.playerName = playerNameValue;

            String payloadValue = extractNullableStringField(body, "payload");
            result.payload = payloadValue;

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String unescape(String value) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\' -> sb.append('\\');
                    case '"' -> sb.append('"');
                    default -> sb.append(c);
                }
                escaped = false;
            } else {
                if (c == '\\') {
                    escaped = true;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String extractStringField(String body, String fieldName) {
        String raw = extractRawField(body, fieldName);
        if (raw == null) {
            return null;
        }
        raw = raw.trim();
        if (!raw.startsWith("\"") || !raw.endsWith("\"")) {
            return null;
        }
        String inner = raw.substring(1, raw.length() - 1);
        return unescape(inner);
    }

    private static String extractNullableStringField(String body, String fieldName) {
        String raw = extractRawField(body, fieldName);
        if (raw == null) {
            return null;
        }
        raw = raw.trim();
        if (raw.equals("null")) {
            return null;
        }
        if (!raw.startsWith("\"") || !raw.endsWith("\"")) {
            return null;
        }
        String inner = raw.substring(1, raw.length() - 1);
        return unescape(inner);
    }

    private static String extractNumberField(String body, String fieldName) {
        String raw = extractRawField(body, fieldName);
        if (raw == null) {
            return null;
        }
        return raw.trim();
    }

    private static String extractRawField(String body, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int namePos = body.indexOf(pattern);
        if (namePos < 0) {
            return null;
        }
        int colonPos = body.indexOf(":", namePos + pattern.length());
        if (colonPos < 0) {
            return null;
        }

        int valueStart = colonPos + 1;
        while (valueStart < body.length() && Character.isWhitespace(body.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= body.length()) {
            return null;
        }

        char first = body.charAt(valueStart);
        if (first == '"') {
            int i = valueStart + 1;
            boolean escaped = false;
            for (; i < body.length(); i++) {
                char c = body.charAt(i);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                }
            }
            if (i >= body.length()) {
                return null;
            }
            return body.substring(valueStart, i + 1);
        } else {
            int i = valueStart;
            while (i < body.length() && body.charAt(i) != ',') {
                i++;
            }
            return body.substring(valueStart, i);
        }
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public int getRoomId() {
        return roomId;
    }

    public void setRoomId(int roomId) {
        this.roomId = roomId;
    }

    public int getPlayerId() {
        return playerId;
    }

    public void setPlayerId(int playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
