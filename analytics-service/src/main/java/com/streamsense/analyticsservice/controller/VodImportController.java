package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.VodImport;
import com.streamsense.analyticsservice.api.VodImportRequest;
import com.streamsense.analyticsservice.api.VodListing;
import com.streamsense.analyticsservice.service.VodImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    /** Imports one recording: creates its session and starts the chat and capture replays. */
    @PostMapping("/{vodId}/import")
    public ResponseEntity<VodImport> importVod(
            @PathVariable("streamer") @NotBlank String streamer,
            @PathVariable("vodId") @NotBlank String vodId,
            @RequestBody(required = false) @Valid VodImportRequest request) {
        VodImport started = imports.importVod(streamer, vodId, request == null ? null : request.averageViewers());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(started);
    }
}
