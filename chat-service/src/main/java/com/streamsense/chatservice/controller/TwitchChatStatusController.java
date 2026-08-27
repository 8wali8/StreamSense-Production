package com.streamsense.chatservice.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.streamsense.chatservice.twitch.TwitchChatLifecycleService;
import com.streamsense.chatservice.twitch.TwitchChatMetrics;
import com.streamsense.chatservice.twitch.TwitchChatStatus;

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
        try {
            return lifecycleService.switchChannels(request.channels());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        }
    }

    public record TwitchChannelRequest(List<String> channels) {
    }
}
