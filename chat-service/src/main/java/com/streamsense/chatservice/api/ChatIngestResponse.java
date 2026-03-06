package com.streamsense.chatservice.api;

public class ChatIngestResponse {
    private String eventId;

    public ChatIngestResponse() {
    }

    public ChatIngestResponse(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}