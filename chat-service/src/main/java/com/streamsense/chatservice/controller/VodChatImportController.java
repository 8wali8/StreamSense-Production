package com.streamsense.chatservice.controller;

import com.streamsense.chatservice.api.VodChatImportRequest;
import com.streamsense.chatservice.api.VodChatImportStatus;
import com.streamsense.chatservice.twitch.TwitchVodChatImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaying a recording's chat into a VOD import, called by analytics-service. */
@RestController
@RequestMapping("/api/chat/replay")
public class VodChatImportController {

    private final TwitchVodChatImportService imports;

    public VodChatImportController(TwitchVodChatImportService imports) {
        this.imports = imports;
    }

    @PostMapping
    public ResponseEntity<VodChatImportStatus> start(@RequestBody @Valid VodChatImportRequest request) {
        // IllegalStateException (already running) -> 409 via GlobalExceptionHandler.
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(imports.start(request));
    }

    @GetMapping("/{vodId}")
    public ResponseEntity<VodChatImportStatus> status(@PathVariable("vodId") String vodId) {
        return imports.status(vodId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }
}
