package com.streamsense.chatservice.controller;

import com.streamsense.chatservice.twitch.TwitchChannelStatus;
import com.streamsense.chatservice.twitch.TwitchChatLifecycleService;
import com.streamsense.chatservice.twitch.TwitchChatMetrics;
import com.streamsense.chatservice.twitch.TwitchChatStatus;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/twitch")
public class TwitchChatStatusController {

    static final String LOGIN_HEADER = "X-StreamSense-Auth-Login";
    static final String ROLE_HEADER = "X-StreamSense-Auth-Role";
    private static final String ROLE_STREAMER = "streamer";

    private final TwitchChatMetrics metrics;
    private final TwitchChatLifecycleService lifecycleService;

    public TwitchChatStatusController(TwitchChatMetrics metrics, TwitchChatLifecycleService lifecycleService) {
        this.metrics = metrics;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/status")
    public TwitchChatStatus status(
            @RequestHeader(name = ROLE_HEADER, required = false) String role,
            @RequestHeader(name = LOGIN_HEADER, required = false) String login) {
        TwitchChatStatus snapshot = metrics.snapshot();
        // A streamer is told about their own channel; which other channels are being measured is not theirs
        // to see. The gateway sets these headers itself and drops any a client sent.
        if (!ROLE_STREAMER.equals(role)) {
            return snapshot;
        }
        String own = normalize(login);
        List<String> mine = snapshot.channels().stream()
                .filter(channel -> normalize(channel).equals(own))
                .toList();
        return new TwitchChatStatus(
                snapshot.enabled(),
                snapshot.state(),
                mine,
                snapshot.lastMessageAt(),
                snapshot.lastError(),
                snapshot.reconnectAttempts());
    }

    @PostMapping("/channels")
    public TwitchChatStatus switchChannels(@RequestBody TwitchChannelRequest request) {
        // IllegalArgumentException -> 400 and IllegalStateException -> 409 via GlobalExceptionHandler.
        return lifecycleService.switchChannels(request.channels());
    }

    /** Starts ingesting one channel without disturbing the others. Idempotent. */
    @PutMapping("/channels/{channel}")
    public TwitchChannelStatus joinChannel(@PathVariable String channel) {
        return channelStatus(channel, lifecycleService.joinChannel(channel));
    }

    /** Stops ingesting one channel, leaving the others alone. Idempotent. */
    @DeleteMapping("/channels/{channel}")
    public TwitchChannelStatus partChannel(@PathVariable String channel) {
        return channelStatus(channel, lifecycleService.partChannel(channel));
    }

    @GetMapping("/channels/{channel}")
    public TwitchChannelStatus channelStatus(@PathVariable String channel) {
        return channelStatus(channel, metrics.snapshot());
    }

    private TwitchChannelStatus channelStatus(String channel, TwitchChatStatus snapshot) {
        return new TwitchChannelStatus(
                normalize(channel), lifecycleService.isJoined(channel), snapshot.enabled(), snapshot.state());
    }

    private static String normalize(String channel) {
        return channel == null ? "" : channel.trim().replaceFirst("^[@#]+", "").toLowerCase(Locale.ROOT);
    }

    public record TwitchChannelRequest(List<String> channels) {}
}
