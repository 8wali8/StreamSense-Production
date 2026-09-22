package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.DealLogo;
import com.streamsense.analyticsservice.service.DealLogoService;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A deal's logos. The channel scope filter confines a streamer to their own deals by the id in the
 * path, as for every other deal route. The logo bytes are served here for the console; the detection
 * pipeline reads them from the object store by the {@code ref} on the logo.
 */
@RestController
@RequestMapping("/api/analytics/deals/{id}/logos")
public class DealLogoController {

    private final DealLogoService logos;

    public DealLogoController(DealLogoService logos) {
        this.logos = logos;
    }

    @GetMapping
    public ResponseEntity<List<DealLogo>> list(@PathVariable("id") long dealId) {
        return ResponseEntity.ok(logos.list(dealId));
    }

    /** Adds a logo from the multipart part {@code file}: 201 with the logo, 404 for an unknown deal, 400 for a bad image, 409 when the deal is full. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DealLogo> upload(@PathVariable("id") long dealId, @RequestPart("file") MultipartFile file)
            throws IOException {
        return logos.upload(dealId, file.getBytes())
                .map(logo -> ResponseEntity.created(
                                URI.create("/api/analytics/deals/" + dealId + "/logos/" + logo.id()))
                        .body(logo))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The image itself, with its stored content type; private to the caller's cache for an hour. */
    @GetMapping("/{logoId}")
    public ResponseEntity<byte[]> image(@PathVariable("id") long dealId, @PathVariable("logoId") long logoId) {
        return logos.read(dealId, logoId)
                .map(stored -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(stored.logo().contentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(stored.bytes()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{logoId}")
    public ResponseEntity<Void> remove(@PathVariable("id") long dealId, @PathVariable("logoId") long logoId) {
        return logos.remove(dealId, logoId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
