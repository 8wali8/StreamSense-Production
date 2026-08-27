package com.streamsense.chatservice.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public class ChatIngestRequest {
    @NotBlank
    private String streamer;

    @NotBlank
    private String user;

    @NotBlank
    private String message;

    // epoch millis (UTC)
    @PositiveOrZero
    private long timestamp;

    public ChatIngestRequest() {
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