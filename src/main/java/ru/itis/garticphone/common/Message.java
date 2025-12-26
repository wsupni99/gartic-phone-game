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
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(message.getType().name()).append("\",");
        sb.append("\"roomId\":").append(message.getRoomId()).append(",");
        sb.append("\"playerId\":").append(message.getPlayerId()).append(",");
        sb.append("\"playerName\":\"").append(escape(message.getPlayerName())).append("\",");
        sb.append("\"payload\":\"").append(escape(message.getPayload())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static Message parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;

        Message message = new Message();
        try {
            String trimmed = json.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null;

            String body = trimmed.substring(1, trimmed.length() - 1);
            String[] parts = body.split(",\"");

            for (String part : parts) {
                int colonIndex = part.indexOf(':');
                if (colonIndex <= 0) continue;

                String rawKey = part.substring(0, colonIndex);
                String value = part.substring(colonIndex + 1);

                String key = rawKey.replace("\"", "");
                String cleaned = stripQuotes(value);

                switch (key) {
                    case "type":
                        message.setType(MessageType.valueOf(cleaned));
                        break;
                    case "roomId":
                        message.setRoomId(Integer.parseInt(cleaned));
                        break;
                    case "playerId":
                        message.setPlayerId(Integer.parseInt(cleaned));
                        break;
                    case "playerName":
                        message.setPlayerName(unescape(cleaned));
                        break;
                    case "payload":
                        message.setPayload(unescape(cleaned));
                    break;
                }
            }
            return message;
        } catch (Exception e) {
            return null;
        }
    }

    public static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static String unescape(String value) {
        if (value == null) return "";
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static String stripQuotes(String value) {
        String v = value.trim();
        if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
            return v.substring(1, v.length() - 1);
        }
        return v;
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
