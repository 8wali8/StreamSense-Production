package com.streamsense.chatservice.controller;

import com.streamsense.chatservice.api.VodChatImportRequest;
import com.streamsense.chatservice.api.VodChatImportStatus;
import com.streamsense.chatservice.service.VodChatImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaying a supplied chat log into a VOD import, called by analytics-service with the log's lines. */
@RestController
@RequestMapping("/api/chat/replay")
public class VodChatImportController {

    private final VodChatImportService imports;

    public VodChatImportController(VodChatImportService imports) {
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

    /** Stops one recording's import and no other; 404 when nothing is known about the recording. Idempotent. */
    @DeleteMapping("/{vodId}")
    public ResponseEntity<VodChatImportStatus> stop(@PathVariable("vodId") String vodId) {
        return imports.stop(vodId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }
}
