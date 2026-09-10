package com.streamsense.apigateway.graphql;

import com.streamsense.apigateway.analytics.SponsorMoments;
import com.streamsense.apigateway.analytics.SponsorMoments.ChatMoment;
import com.streamsense.apigateway.analytics.SponsorMoments.ExposureSegment;
import com.streamsense.apigateway.analytics.SponsorMoments.HighlightMoment;
import com.streamsense.apigateway.analytics.SponsorMoments.RiskSpike;
import com.streamsense.apigateway.analytics.SponsorMoments.VoiceMention;
import com.streamsense.apigateway.analytics.StreamMetricBucket;
import com.streamsense.apigateway.analytics.StreamSession;
import com.streamsense.apigateway.events.SentimentAnalysisEvent;
import com.streamsense.apigateway.events.SponsorDetectionEvent;
import com.streamsense.apigateway.events.TranscriptSentimentEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure assembly of the report timeline from the three stores. Detections collapse into on-screen
 * segments, relevant chat lines group per minute, voice lines stand alone, and the buckets flagged
 * as negative spikes become risk marks. The best moment is the strongest positive chat minute; the
 * weakest is the most negative mention, chat or voice, when there is one.
 */
final class SponsorMomentsComposer {

    /** Two detections closer than this belong to one on-screen run (capture samples every ~10s). */
    static final long DEFAULT_GAP_MS = 30_000L;

    private static final long MINUTE_MS = 60_000L;

    private SponsorMomentsComposer() {}

    static SponsorMoments compose(
            StreamSession session,
            String sponsor,
            List<SponsorDetectionEvent> detections,
            List<SentimentAnalysisEvent> chat,
            List<TranscriptSentimentEvent> voice,
            List<StreamMetricBucket> buckets,
            long gapMs) {
        long start = session.startedAt();
        List<ExposureSegment> segments = segments(sponsor, detections, start, gapMs);
        List<VoiceMention> voiceMentions = voice.stream()
                .filter(event -> matches(sponsor, event.getMatchedSponsor()))
                // Anchored at the segment's start: the words are somewhere inside the ten seconds that follow,
                // so a VOD link there lands just before them rather than after them.
                .sorted(Comparator.comparingLong(TranscriptSentimentEvent::getSegmentStartedAt))
                .map(event -> new VoiceMention(
                        event.getSentimentEventId(),
                        event.getSegmentStartedAt(),
                        Math.max(0, event.getSegmentStartedAt() - start),
                        event.getLabel(),
                        event.getScore(),
                        event.getText()))
                .toList();
        List<ChatMoment> chatMoments = chatMoments(sponsor, chat, start);
        List<RiskSpike> spikes = buckets.stream()
                .filter(StreamMetricBucket::negativeSpike)
                .map(bucket -> new RiskSpike(
                        bucket.bucketStart(),
                        Math.max(0, bucket.bucketStart() - start),
                        bucket.chatNegativeRatio(),
                        bucket.chatMessageCount()))
                .toList();
        return new SponsorMoments(
                session,
                sponsor,
                segments,
                voiceMentions,
                chatMoments,
                spikes,
                best(chatMoments, segments),
                weakest(chatMoments, voiceMentions));
    }

    private static List<ExposureSegment> segments(
            String sponsor, List<SponsorDetectionEvent> detections, long start, long gapMs) {
        List<SponsorDetectionEvent> ordered = detections.stream()
                .filter(event -> matches(sponsor, event.getSponsor()))
                .sorted(Comparator.comparingLong(SponsorDetectionEvent::getCapturedAt))
                .toList();
        List<ExposureSegment> segments = new ArrayList<>();
        SponsorDetectionEvent first = null;
        SponsorDetectionEvent last = null;
        int count = 0;
        double peak = 0;
        Long videoTs = null;
        for (SponsorDetectionEvent event : ordered) {
            if (last != null && event.getCapturedAt() - last.getCapturedAt() > gapMs) {
                segments.add(segment(first, last, count, peak, videoTs, start));
                first = null;
                count = 0;
                peak = 0;
                videoTs = null;
            }
            if (first == null) {
                first = event;
            }
            if (videoTs == null && event.getVideoTimestampMs() != null) {
                videoTs = event.getVideoTimestampMs();
            }
            last = event;
            count++;
            peak = Math.max(peak, event.getConfidence());
        }
        if (first != null) {
            segments.add(segment(first, last, count, peak, videoTs, start));
        }
        return segments;
    }

    private static ExposureSegment segment(
            SponsorDetectionEvent first, SponsorDetectionEvent last, int count, double peak, Long videoTs, long start) {
        long startedAt = first.getCapturedAt();
        long endedAt = last.getCapturedAt();
        return new ExposureSegment(
                first.getSponsor(),
                startedAt,
                endedAt,
                Math.max(0, startedAt - start),
                Math.max(0, endedAt - startedAt),
                videoTs,
                count,
                round(peak));
    }

    private static List<ChatMoment> chatMoments(String sponsor, List<SentimentAnalysisEvent> chat, long start) {
        Map<Long, List<SentimentAnalysisEvent>> byMinute = new TreeMap<>();
        for (SentimentAnalysisEvent event : chat) {
            if (!matches(sponsor, event.getMatchedSponsor())) {
                continue;
            }
            long minute = Math.floorDiv(event.getChatTimestamp(), MINUTE_MS) * MINUTE_MS;
            byMinute.computeIfAbsent(minute, key -> new ArrayList<>()).add(event);
        }
        List<ChatMoment> moments = new ArrayList<>();
        for (Map.Entry<Long, List<SentimentAnalysisEvent>> entry : byMinute.entrySet()) {
            List<SentimentAnalysisEvent> lines = entry.getValue();
            long positive = lines.stream()
                    .filter(line -> "POSITIVE".equalsIgnoreCase(line.getLabel()))
                    .count();
            long negative = lines.stream()
                    .filter(line -> "NEGATIVE".equalsIgnoreCase(line.getLabel()))
                    .count();
            double average = lines.stream()
                    .mapToDouble(SentimentAnalysisEvent::getScore)
                    .average()
                    .orElse(0);
            SentimentAnalysisEvent sample = lines.stream()
                    .max(Comparator.comparingDouble(line -> Math.abs(line.getScore())))
                    .orElse(lines.get(0));
            moments.add(new ChatMoment(
                    entry.getKey(),
                    Math.max(0, entry.getKey() - start),
                    lines.size(),
                    round((double) positive / lines.size()),
                    round((double) negative / lines.size()),
                    round(average),
                    sample.getMessage(),
                    sample.getUser()));
        }
        return moments;
    }

    private static HighlightMoment best(List<ChatMoment> chatMoments, List<ExposureSegment> segments) {
        ChatMoment top = chatMoments.stream()
                .filter(moment -> moment.positiveShare() > 0)
                .max(Comparator.comparingDouble((ChatMoment moment) -> moment.count() * moment.positiveShare())
                        .thenComparing(Comparator.comparingLong(ChatMoment::at).reversed()))
                .orElse(null);
        if (top == null) {
            return null;
        }
        boolean onScreen = segments.stream()
                .anyMatch(segment -> segment.startedAt() <= top.at() + MINUTE_MS && segment.endedAt() >= top.at());
        String title = top.count() + (top.count() == 1 ? " brand mention" : " brand mentions") + " in a minute, "
                + Math.round(top.positiveShare() * 100) + "% positive" + (onScreen ? ", logo on screen" : "");
        return new HighlightMoment(
                "CHAT_BURST",
                top.at(),
                top.offsetMs(),
                title,
                top.sample() == null ? "" : top.sample(),
                round(top.count() * top.positiveShare()));
    }

    private static HighlightMoment weakest(List<ChatMoment> chatMoments, List<VoiceMention> voiceMentions) {
        VoiceMention voice = voiceMentions.stream()
                .filter(mention -> "NEGATIVE".equalsIgnoreCase(mention.label()))
                .min(Comparator.comparingDouble(VoiceMention::score))
                .orElse(null);
        ChatMoment chat = chatMoments.stream()
                .filter(moment -> moment.negativeShare() > 0)
                .min(Comparator.comparingDouble(ChatMoment::averageScore))
                .orElse(null);
        if (voice == null && chat == null) {
            return null;
        }
        if (voice != null && (chat == null || voice.score() <= chat.averageScore())) {
            return new HighlightMoment(
                    "VOICE",
                    voice.at(),
                    voice.offsetMs(),
                    "Voice mention read as negative, " + voice.score(),
                    voice.text(),
                    voice.score());
        }
        return new HighlightMoment(
                "CHAT",
                chat.at(),
                chat.offsetMs(),
                Math.round(chat.negativeShare() * 100) + "% of " + chat.count() + " brand mentions negative",
                chat.sample() == null ? "" : chat.sample(),
                chat.averageScore());
    }

    private static boolean matches(String sponsor, String candidate) {
        if (sponsor == null || sponsor.isBlank()) {
            return candidate != null && !candidate.isBlank();
        }
        return candidate != null
                && candidate
                        .trim()
                        .toLowerCase(Locale.ROOT)
                        .equals(sponsor.trim().toLowerCase(Locale.ROOT));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }
}
