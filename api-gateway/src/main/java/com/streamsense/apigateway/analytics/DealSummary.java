package com.streamsense.apigateway.analytics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DealSummary(Deal deal, Totals totals, List<SessionSummary> sessions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Totals(
            int streams,
            int liveStreams,
            long streamedMs,
            long onScreenMs,
            Double onScreenShare,
            long mentions,
            long chatMentions,
            long voiceMentions,
            Double mentionSentiment,
            Double averageViewers,
            Double logoValue,
            Double hostReadValue,
            Double mediaValue,
            long commandUses,
            long linkPosts) {}
}
