package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.Deal;
import com.streamsense.analyticsservice.service.DealService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resolves a share token to its deal, for the gateway's read-only share mode. */
@Validated
@RestController
@RequestMapping("/api/analytics/share")
public class ShareLinkController {

    private final DealService deals;

    public ShareLinkController(DealService deals) {
        this.deals = deals;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Deal> resolve(@PathVariable("token") @NotBlank String token) {
        return deals.resolveShareToken(token).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }
}
