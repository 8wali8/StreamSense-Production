package com.streamsense.videoservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The channel's current deal as analytics-service answers it: the sponsor every detection is stamped
 * with and the logos to look for (the deal's other terms are its own business).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CurrentDeal(long id, String sponsor, List<Logo> logos) {

    public CurrentDeal {
        logos = logos == null ? List.of() : List.copyOf(logos);
    }

    /** A logo on the deal: its id, and the {@code s3://} URI the detector reads the image by. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Logo(long id, String ref) {}

    public boolean hasLogo() {
        return !logos.isEmpty();
    }
}
