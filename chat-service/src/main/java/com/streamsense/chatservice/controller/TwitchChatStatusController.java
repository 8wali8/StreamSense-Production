package com.streamsense.chatservice.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.streamsense.chatservice.twitch.TwitchChatMetrics;
import com.streamsense.chatservice.twitch.TwitchChatStatus;

@RestController
@RequestMapping("/api/chat/twitch")
public class TwitchChatStatusController {

    private final TwitchChatMetrics metrics;

    public TwitchChatStatusController(TwitchChatMetrics metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/status")
    public TwitchChatStatus status() {
        return metrics.snapshot();
    }
}
