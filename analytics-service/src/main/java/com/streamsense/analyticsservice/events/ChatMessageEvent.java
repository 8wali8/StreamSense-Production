package com.streamsense.analyticsservice.events;

public class ChatMessageEvent {
    private String eventId;
    private String streamer;
    private String user;
    private String message;
    private long timestamp;
    private String source;
    private String channelLogin;
    private String streamSessionId;
    private String twitchStreamId;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getChannelLogin() {
        return channelLogin;
    }

    public void setChannelLogin(String channelLogin) {
        this.channelLogin = channelLogin;
    }

    public String getStreamSessionId() {
        return streamSessionId;
    }

    public void setStreamSessionId(String streamSessionId) {
        this.streamSessionId = streamSessionId;
    }

    public String getTwitchStreamId() {
        return twitchStreamId;
    }

    public void setTwitchStreamId(String twitchStreamId) {
        this.twitchStreamId = twitchStreamId;
    }

}
