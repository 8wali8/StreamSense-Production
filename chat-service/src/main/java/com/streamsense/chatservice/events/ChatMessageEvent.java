package com.streamsense.chatservice.events;

public class ChatMessageEvent {
    private String eventId;
    private String streamer;
    private String user;
    private String message;
    private long timestamp; // epoch millis (UTC)

    public ChatMessageEvent() {
    }

    public ChatMessageEvent(String eventId, String streamer, String user, String message, long timestamp) {
        this.eventId = eventId;
        this.streamer = streamer;
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getStreamer() {
        return streamer;
    }

    public void setStreamer(String streamer) {
        this.streamer = streamer;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}