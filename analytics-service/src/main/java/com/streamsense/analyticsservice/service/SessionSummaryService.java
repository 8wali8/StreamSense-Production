package com.streamsense.analyticsservice.service;

import com.streamsense.analyticsservice.api.DirectResponse;
import com.streamsense.analyticsservice.api.SessionSummary;
import com.streamsense.analyticsservice.api.SessionValue;
import com.streamsense.analyticsservice.api.StreamMetricsSummary;
import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.SummaryOptions;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.model.CommandTotals;
import com.streamsense.analyticsservice.model.SponsorBucketExposure;
import com.streamsense.analyticsservice.model.SponsorMentionTotals;
import com.streamsense.analyticsservice.model.ViewerSample;
import com.streamsense.analyticsservice.persistence.ChatResponseRepository;
import com.streamsense.analyticsservice.persistence.SponsorMentionRepository;
import com.streamsense.analyticsservice.persistence.SponsorMetricBucketRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The one-page session report in one call: exposure, mentions, viewers, risk, media value, and
 * direct response for one sponsor in one session. Numbers come from the per-minute buckets between
 * the session's start and end; nothing is re-aggregated per session.
 */
@Service
public class SessionSummaryService {

    private final StreamSessionService sessions;
    private final MetricQueryService metrics;
    private final SponsorMetricBucketRepository sponsorBuckets;
    private final SponsorMentionRepository mentions;
    private final ChatResponseRepository responses;
    private final StreamSenseProperties properties;

    public SessionSummaryService(
            StreamSessionService sessions,
            MetricQueryService metrics,
            SponsorMetricBucketRepository sponsorBuckets,
            SponsorMentionRepository mentions,
            ChatResponseRepository responses,
            StreamSenseProperties properties) {
        this.sessions = sessions;
        this.metrics = metrics;
        this.sponsorBuckets = sponsorBuckets;
        this.mentions = mentions;
        this.responses = responses;
        this.properties = properties;
    }

    public Optional<SessionSummary> summary(long sessionId, SummaryOptions options) {
        Optional<StreamSession> found = sessions.get(sessionId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        StreamSession session = found.get();
        long from = session.startedAt();
        long to = session.endedAt() == null ? session.startedAt() + session.durationMs() : session.endedAt();
        MetricQueryService.QueryWindow window = metrics.rangeWindow(session.streamer(), from, to);
        StreamMetricsSummary base = metrics.summary(window);

        String sponsor = clean(options.sponsor());
        if (sponsor == null && !base.sponsorExposure().topSponsors().isEmpty()) {
            sponsor = base.sponsorExposure().topSponsors().get(0).sponsor();
        }

        long onScreenMs = 0;
        List<SponsorBucketExposure> exposure = List.of();
        List<SponsorMentionTotals> mentionTotals = List.of();
        if (sponsor != null) {
            exposure = sponsorBuckets.findExposureByBucket(
                    window.streamer(),
                    null,
                    window.windowStart(),
                    window.windowEnd(),
                    window.bucketSizeSeconds(),
                    sponsor);
            onScreenMs = Math.min(
                    Math.max(0, session.durationMs()),
                    exposure.stream()
                            .mapToLong(SponsorBucketExposure::estimatedExposureMs)
                            .sum());
            mentionTotals = mentions.findTotals(
                    window.streamer(),
                    null,
                    window.windowStart(),
                    window.windowEnd(),
                    window.bucketSizeSeconds(),
                    sponsor);
        }
        long chatMentions = channelCount(mentionTotals, SponsorMentionRepository.CHANNEL_CHAT);
        long voiceMentions = channelCount(mentionTotals, SponsorMentionRepository.CHANNEL_VOICE);
        long mentionCount = chatMentions + voiceMentions;
        long positive = mentionTotals.stream()
                .mapToLong(SponsorMentionTotals::positiveCount)
                .sum();
        long negative = mentionTotals.stream()
                .mapToLong(SponsorMentionTotals::negativeCount)
                .sum();
        double scoreSum = mentionTotals.stream()
                .mapToDouble(SponsorMentionTotals::scoreSum)
                .sum();

        List<ViewerSample> samples = sessions.viewerSamples(sessionId);
        SessionValue value = value(exposure, voiceMentions, samples, session, options);
        DirectResponse response = response(window, options);

        return Optional.of(new SessionSummary(
                session,
                sponsor,
                onScreenMs,
                session.durationMs() <= 0 ? null : round((double) onScreenMs / session.durationMs()),
                mentionCount,
                chatMentions,
                voiceMentions,
                mentionCount == 0 ? null : round(scoreSum / mentionCount),
                mentionCount == 0 ? null : round((double) positive / mentionCount),
                mentionCount == 0 ? null : round((double) negative / mentionCount),
                session.averageViewers(),
                session.peakViewers(),
                base.risk(),
                base.chat(),
                base.chatSentiment(),
                base.transcriptSentiment(),
                base.engagement(),
                value,
                response));
    }

    /**
     * Logo value: every accepted-detection minute weighted by who was watching and how prominent
     * the box was, priced per thousand 30-second equivalents. Host reads: each voice mention reaches
     * the session's average viewers, priced per thousand listeners. Null when no viewers were sampled.
     */
    private SessionValue value(
            List<SponsorBucketExposure> exposure,
            long voiceMentions,
            List<ViewerSample> samples,
            StreamSession session,
            SummaryOptions options) {
        StreamSenseProperties.Value config = properties.getAnalytics().getValue();
        double cpm =
                options.cpmPer30sEquivalent() == null ? config.getCpmPer30sEquivalent() : options.cpmPer30sEquivalent();
        double rate =
                options.hostReadRatePer1000() == null ? config.getHostReadRatePer1000() : options.hostReadRatePer1000();
        String basis = "logo: weighted viewer-minutes x 2 per 1,000 at CPM " + cpm
                + "; host reads: voice mentions x average viewers per 1,000 at " + rate
                + "; prominence weight " + config.getProminenceBase() + " + min(" + (1.0d - config.getProminenceBase())
                + ", box area x " + config.getProminenceAreaScale() + ")";
        if (samples.isEmpty() || session.averageViewers() == null) {
            return new SessionValue(null, null, null, null, null, cpm, rate, basis);
        }
        double weightedViewerMinutes = 0;
        double prominenceSum = 0;
        int prominenceCount = 0;
        for (SponsorBucketExposure bucket : exposure) {
            if (bucket.estimatedExposureMs() <= 0) {
                continue;
            }
            double area = bucket.acceptedDetectionCount() == 0 ? 0 : bucket.areaSum() / bucket.acceptedDetectionCount();
            double weight = config.getProminenceBase()
                    + Math.min(1.0d - config.getProminenceBase(), area * config.getProminenceAreaScale());
            prominenceSum += weight;
            prominenceCount++;
            weightedViewerMinutes +=
                    (bucket.estimatedExposureMs() / 60_000.0d) * viewersAt(samples, bucket.bucketStart()) * weight;
        }
        double logoValue = weightedViewerMinutes * 2.0d / 1000.0d * cpm;
        double hostReadValue = voiceMentions * session.averageViewers() / 1000.0d * rate;
        return new SessionValue(
                round2(logoValue),
                round2(hostReadValue),
                round2(logoValue + hostReadValue),
                round(weightedViewerMinutes),
                prominenceCount == 0 ? null : round(prominenceSum / prominenceCount),
                cpm,
                rate,
                basis);
    }

    /** The last viewer sample at or before the bucket, or the first one when the bucket predates sampling. */
    private double viewersAt(List<ViewerSample> samples, long bucketStart) {
        double viewers = samples.get(0).viewerCount();
        for (ViewerSample sample : samples) {
            if (sample.sampledAt() > bucketStart) {
                break;
            }
            viewers = sample.viewerCount();
        }
        return viewers;
    }

    private DirectResponse response(MetricQueryService.QueryWindow window, SummaryOptions options) {
        StreamSenseProperties.Response config = properties.getAnalytics().getResponse();
        String command = ChatSignals.normalizeCommand(
                options.chatCommand() == null ? config.getDefaultChatCommand() : options.chatCommand());
        String host = ChatSignals.normalizeHost(
                options.trackedLinkHost() == null ? config.getDefaultTrackedLinkHost() : options.trackedLinkHost());
        CommandTotals totals = command == null
                ? new CommandTotals(0, 0)
                : responses.commandTotals(
                        window.streamer(),
                        null,
                        window.windowStart(),
                        window.windowEnd(),
                        window.bucketSizeSeconds(),
                        command);
        long links = host == null
                ? 0
                : responses.linkPosts(
                        window.streamer(),
                        null,
                        window.windowStart(),
                        window.windowEnd(),
                        window.bucketSizeSeconds(),
                        host);
        return new DirectResponse(command, totals.uses(), totals.users(), host, links);
    }

    private long channelCount(List<SponsorMentionTotals> totals, String channel) {
        return totals.stream()
                .filter(total -> channel.equals(total.channel()))
                .mapToLong(SponsorMentionTotals::mentionCount)
                .sum();
    }

    private Double round(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }

    private Double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
