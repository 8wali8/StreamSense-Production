package com.streamsense.chatservice.controller;

import com.streamsense.chatservice.twitch.TwitchChatLifecycleService;
import com.streamsense.chatservice.twitch.TwitchChatMetrics;
import com.streamsense.chatservice.twitch.TwitchChatStatus;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/twitch")
public class TwitchChatStatusController {

    private final TwitchChatMetrics metrics;
    private final TwitchChatLifecycleService lifecycleService;

    public TwitchChatStatusController(TwitchChatMetrics metrics, TwitchChatLifecycleService lifecycleService) {
        this.metrics = metrics;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/status")
    public TwitchChatStatus status() {
        return metrics.snapshot();
    }

    @PostMapping("/channels")
    public TwitchChatStatus switchChannels(@RequestBody TwitchChannelRequest request) {
        // IllegalArgumentException -> 400 and IllegalStateException -> 409 via GlobalExceptionHandler.
        return lifecycleService.switchChannels(request.channels());
    }

    public record TwitchChannelRequest(List<String> channels) {}
}
