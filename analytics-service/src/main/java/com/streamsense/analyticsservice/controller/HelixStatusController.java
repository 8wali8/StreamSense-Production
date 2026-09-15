package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.HelixPollStatus;
import com.streamsense.analyticsservice.twitch.StreamSessionPoller;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Helix poller's last result for the operations page; {@code enabled: false} when the poller is off. */
@RestController
@RequestMapping("/api/analytics")
public class HelixStatusController {

    private final ObjectProvider<StreamSessionPoller> poller;

    public HelixStatusController(ObjectProvider<StreamSessionPoller> poller) {
        this.poller = poller;
    }

    @GetMapping("/helix/status")
    public HelixPollStatus status() {
        StreamSessionPoller running = poller.getIfAvailable();
        return running == null ? HelixPollStatus.disabled() : running.status();
    }
}
