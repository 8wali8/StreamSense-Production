package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.VodChatLogSummary;
import com.streamsense.analyticsservice.api.VodImport;
import com.streamsense.analyticsservice.api.VodImportRequest;
import com.streamsense.analyticsservice.api.VodImportStatus;
import com.streamsense.analyticsservice.api.VodListing;
import com.streamsense.analyticsservice.service.VodImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A channel's recordings and their imports. Every route is under the channel-scoped path, so a
 * streamer reaches their own channel's and the operator any; the services the import runs on are
 * never called by the console directly.
 */
@Validated
@RestController
@RequestMapping("/api/analytics/streams/{streamer}/vods")
public class VodImportController {

    private final VodImportService imports;

    public VodImportController(VodImportService imports) {
        this.imports = imports;
    }

    /** The channel's recordings on Twitch, newest first. 409 when Helix is not configured. */
    @GetMapping
    public List<VodListing> list(
            @PathVariable("streamer") @NotBlank String streamer,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(100) Integer limit) {
        return imports.list(streamer, limit == null ? 20 : limit);
    }

    /** Where every import the channel asked for stands; the active ones are brought up to date first. */
    @GetMapping("/imports")
    public List<VodImportStatus> statuses(@PathVariable("streamer") @NotBlank String streamer) {
        return imports.statuses(streamer);
    }

    /** Imports one recording: creates its session and starts (or resumes) the chat and capture replays. */
    @PostMapping("/{vodId}/import")
    public ResponseEntity<VodImport> importVod(
            @PathVariable("streamer") @NotBlank String streamer,
            @PathVariable("vodId") @NotBlank String vodId,
            @RequestBody(required = false) @Valid VodImportRequest request) {
        VodImport started = imports.importVod(streamer, vodId, request == null ? null : request.averageViewers());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(started);
    }

    /** The most text a chat log upload may carry; the console's nginx allows a little more on /api/. */
    static final int MAX_CHAT_LOG_BYTES = 25 * 1024 * 1024;

    /**
     * Keeps a chat log for one recording (JSON, CSV, or a chat client's text log, sent as the request body).
     * {@code timezone} is the zone a text log's wall-clock times are in, UTC when absent.
     */
    @PutMapping(value = "/{vodId}/chat-log", consumes = "*/*")
    public VodChatLogSummary uploadChatLog(
            @PathVariable("streamer") @NotBlank String streamer,
            @PathVariable("vodId") @NotBlank String vodId,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "timezone", required = false) String timezone,
            @RequestBody(required = false) String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("the chat log is empty");
        }
        if (content.length() > MAX_CHAT_LOG_BYTES) {
            throw new IllegalArgumentException("the chat log is larger than 25 MB");
        }
        ZoneId zone;
        try {
            zone = timezone == null || timezone.isBlank() ? ZoneId.of("UTC") : ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("unknown time zone " + timezone);
        }
        String name = fileName == null ? null : fileName.strip();
        return imports.uploadChatLog(streamer, vodId, name == null || name.isEmpty() ? null : name, zone, content);
    }

    /** Stops one recording's import on both services. Idempotent; 400 when the recording was never imported. */
    @PostMapping("/{vodId}/stop")
    public VodImportStatus stop(
            @PathVariable("streamer") @NotBlank String streamer, @PathVariable("vodId") @NotBlank String vodId) {
        return imports.stop(streamer, vodId);
    }
}
