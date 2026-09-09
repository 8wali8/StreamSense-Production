package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.service.StreamSessionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/analytics")
public class StreamSessionController {

    private final StreamSessionService sessions;

    public StreamSessionController(StreamSessionService sessions) {
        this.sessions = sessions;
    }

    /** Sessions of a streamer overlapping [from, to) in epoch millis, newest first. */
    @GetMapping("/streams/{streamer}/sessions")
    public List<StreamSession> sessions(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "from", required = false) Long from,
            @RequestParam(value = "to", required = false) Long to,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(200) Integer limit) {
        return sessions.list(streamer, from, to, limit);
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<StreamSession> session(@PathVariable("id") long id) {
        return sessions.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }
}
