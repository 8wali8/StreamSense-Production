package com.streamsense.analyticsservice.controller;

import com.streamsense.analyticsservice.api.Deal;
import com.streamsense.analyticsservice.api.DealCreateRequest;
import com.streamsense.analyticsservice.api.DealSummary;
import com.streamsense.analyticsservice.service.DealService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/analytics/deals")
public class DealController {

    private final DealService deals;

    public DealController(DealService deals) {
        this.deals = deals;
    }

    @PostMapping
    public ResponseEntity<Deal> create(@RequestBody @Valid DealCreateRequest request) {
        Deal created = deals.create(request);
        return ResponseEntity.created(URI.create("/api/analytics/deals/" + created.id()))
                .body(created);
    }

    /** A streamer's deals, or every deal when no streamer is given; newest start first. */
    @GetMapping
    public List<Deal> list(
            @RequestParam(value = "streamer", required = false) String streamer,
            @RequestParam(value = "limit", required = false) @Min(1) @Max(200) Integer limit) {
        return deals.list(streamer, limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Deal> get(@PathVariable("id") long id) {
        return deals.get(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }

    /** The deal with its totals and every session report inside its dates. */
    @GetMapping("/{id}/summary")
    public ResponseEntity<DealSummary> summary(@PathVariable("id") long id) {
        return deals.summary(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound()
                .build());
    }
}
