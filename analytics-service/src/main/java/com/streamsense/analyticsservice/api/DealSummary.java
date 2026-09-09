package com.streamsense.analyticsservice.api;

import java.util.List;

/**
 * How one deal is going: the deal, the totals across the streams inside its dates, and each of
 * those streams' report numbers (newest first). Media value is null until at least one stream
 * had viewer samples; {@code mentionSentiment} is weighted by mention count.
 */
public record DealSummary(Deal deal, Totals totals, List<SessionSummary> sessions) {

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
