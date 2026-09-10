package com.streamsense.analyticsservice.service;

import com.streamsense.analyticsservice.api.Deal;
import com.streamsense.analyticsservice.api.DealCreateRequest;
import com.streamsense.analyticsservice.api.DealSummary;
import com.streamsense.analyticsservice.api.SessionSummary;
import com.streamsense.analyticsservice.api.ShareLink;
import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.SummaryOptions;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.model.DealRow;
import com.streamsense.analyticsservice.persistence.DealRepository;
import com.streamsense.analyticsservice.relevance.SponsorRelevancePointer;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deals: create, list, read, and the roll-up of every session inside a deal's dates. A deal points
 * relevance scoring at its sponsor for the channel from the moment it covers now: at creation when
 * its dates already do, otherwise when {@link #activateStartedDeals()} sees it begin.
 */
@Service
public class DealService {

    static final int MAX_LIMIT = 200;
    private static final Pattern COMMAND = Pattern.compile("^![A-Za-z0-9_-]{1,63}$");

    private static final Logger log = LoggerFactory.getLogger(DealService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DealRepository deals;
    private final StreamSessionService sessions;
    private final SessionSummaryService summaries;
    private final StreamSenseProperties properties;
    private final ObjectProvider<SponsorRelevancePointer> relevance;
    private final Clock clock;
    /** Deals this instance has pointed relevance at; a restart points the running ones once more, which is harmless. */
    private final Set<Long> activated = ConcurrentHashMap.newKeySet();

    @Autowired
    public DealService(
            DealRepository deals,
            StreamSessionService sessions,
            SessionSummaryService summaries,
            StreamSenseProperties properties,
            ObjectProvider<SponsorRelevancePointer> relevance) {
        this(deals, sessions, summaries, properties, relevance, Clock.systemUTC());
    }

    /** For tests that need a fixed clock, for example to see a scheduled deal begin. */
    public DealService(
            DealRepository deals,
            StreamSessionService sessions,
            SessionSummaryService summaries,
            StreamSenseProperties properties,
            ObjectProvider<SponsorRelevancePointer> relevance,
            Clock clock) {
        this.deals = deals;
        this.sessions = sessions;
        this.summaries = summaries;
        this.properties = properties;
        this.relevance = relevance;
        this.clock = clock;
    }

    public Deal create(DealCreateRequest request) {
        String streamer = normalizeLogin(request.streamer());
        String sponsor = clean(request.sponsor());
        if (streamer == null || sponsor == null) {
            throw new IllegalArgumentException("streamer and sponsor are required");
        }
        if (request.endsAt() != null && request.endsAt() <= request.startsAt()) {
            throw new IllegalArgumentException("endsAt must be after startsAt");
        }
        String trackedLink = clean(request.trackedLink());
        if (trackedLink != null && trackedLinkHost(trackedLink) == null) {
            throw new IllegalArgumentException("trackedLink must be an absolute http(s) URL");
        }
        String command = normalizeCommand(request.chatCommand());
        StreamSenseProperties.Value value = properties.getAnalytics().getValue();
        long now = clock.millis();
        DealRow row = new DealRow(
                0,
                streamer,
                sponsor,
                request.startsAt(),
                request.endsAt(),
                request.promisedStreams(),
                request.fee(),
                Optional.ofNullable(clean(request.currency()))
                        .map(c -> c.toUpperCase(Locale.ROOT))
                        .orElse("USD"),
                request.cpmPer30sEquivalent() != null ? request.cpmPer30sEquivalent() : value.getCpmPer30sEquivalent(),
                request.hostReadRatePer1000() != null ? request.hostReadRatePer1000() : value.getHostReadRatePer1000(),
                trackedLink,
                command,
                clean(request.channelPointReward()),
                null,
                now);
        long id = deals.insert(row, now);
        DealRow stored = deals.findById(id).orElseThrow();
        if (stored.covers(now)) {
            activate(stored);
        }
        return toApi(stored, now);
    }

    /**
     * Points relevance at the sponsor of every deal that has begun since the last check: for each
     * streamer with a running deal, the newest one covering now, once per deal. Runs every minute,
     * so a deal scheduled for a future date takes effect within a minute of its start.
     */
    @Scheduled(fixedDelayString = "${streamsense.analytics.deal-activation-check-ms:60000}")
    public int activateStartedDeals() {
        long now = clock.millis();
        int pointed = 0;
        for (String streamer : deals.findStreamersWithDealCovering(now)) {
            Optional<DealRow> current =
                    deals.findCovering(streamer, now).stream().findFirst();
            if (current.isPresent() && !activated.contains(current.get().id()) && activate(current.get())) {
                pointed++;
            }
        }
        return pointed;
    }

    private boolean activate(DealRow deal) {
        activated.add(deal.id());
        SponsorRelevancePointer pointer = relevance.getIfAvailable();
        if (pointer == null) {
            return false;
        }
        log.info("deal {} for @{} began: pointing relevance at {}", deal.id(), deal.streamer(), deal.sponsor());
        pointer.point(deal.streamer(), deal.sponsor());
        return true;
    }

    /** A streamer's deals, or every deal when no streamer is given; newest start first. */
    public List<Deal> list(String streamer, Integer requestedLimit) {
        int limit = requestedLimit == null ? 50 : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        long now = clock.millis();
        String login = normalizeLogin(streamer);
        List<DealRow> rows = login == null ? deals.findAll(limit) : deals.findByStreamer(login, limit);
        return rows.stream().map(row -> toApi(row, now)).toList();
    }

    public Optional<Deal> get(long id) {
        long now = clock.millis();
        return deals.findById(id).map(row -> toApi(row, now));
    }

    /** The deal's share token, minted on first use and kept afterwards so a sent link keeps working. */
    public Optional<ShareLink> share(long id) {
        return deals.findById(id).map(deal -> {
            if (deal.shareToken() != null) {
                return new ShareLink(deal.id(), deal.shareToken());
            }
            byte[] bytes = new byte[24];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            deals.updateShareToken(deal.id(), token, clock.millis());
            return new ShareLink(deal.id(), token);
        });
    }

    /** Clears the share token; every link carrying it stops working. */
    public boolean unshare(long id) {
        Optional<DealRow> found = deals.findById(id);
        found.ifPresent(deal -> deals.updateShareToken(deal.id(), null, clock.millis()));
        return found.isPresent();
    }

    /** The deal a share token opens, or empty when the token is unknown or revoked. */
    public Optional<Deal> resolveShareToken(String token) {
        String cleaned = clean(token);
        if (cleaned == null || cleaned.length() > 64) {
            return Optional.empty();
        }
        long now = clock.millis();
        return deals.findByShareToken(cleaned).map(row -> toApi(row, now));
    }

    /** Streamers with a deal running now, for the Helix poller's watch list. */
    public List<String> streamersWithActiveDeals() {
        return deals.findStreamersWithDealCovering(clock.millis());
    }

    /** The deal a session belongs to: the newest one on its channel covering its start, for the sponsor if named. */
    public Optional<DealRow> dealFor(String streamer, long startedAt, String sponsor) {
        String login = normalizeLogin(streamer);
        if (login == null) {
            return Optional.empty();
        }
        List<DealRow> covering = deals.findCovering(login, startedAt);
        String wanted = clean(sponsor);
        if (wanted != null) {
            return covering.stream()
                    .filter(row -> row.sponsor().equalsIgnoreCase(wanted))
                    .findFirst();
        }
        return covering.stream().findFirst();
    }

    /** The options a deal implies for a session summary: its sponsor, rates, command, and link host. */
    public static SummaryOptions optionsFor(DealRow deal) {
        return new SummaryOptions(
                deal.sponsor(),
                deal.chatCommand(),
                trackedLinkHost(deal.trackedLink()),
                deal.cpmPer30sEquivalent(),
                deal.hostReadRatePer1000());
    }

    public Optional<DealSummary> summary(long id) {
        long now = clock.millis();
        Optional<DealRow> found = deals.findById(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        DealRow deal = found.get();
        long to = deal.endsAt() == null ? now + 1 : Math.min(deal.endsAt(), now + 1);
        SummaryOptions options = optionsFor(deal);
        List<SessionSummary> reports = sessions.list(deal.streamer(), deal.startsAt(), to, MAX_LIMIT).stream()
                .filter(session -> session.startedAt() >= deal.startsAt())
                .filter(session -> deal.endsAt() == null || session.startedAt() < deal.endsAt())
                .map(session -> summaries.summary(session.id(), options))
                .flatMap(Optional::stream)
                .toList();
        return Optional.of(new DealSummary(toApi(deal, now), totals(reports), reports));
    }

    static DealSummary.Totals totals(List<SessionSummary> reports) {
        long streamedMs = 0;
        long onScreenMs = 0;
        long mentions = 0;
        long chat = 0;
        long voice = 0;
        double sentimentWeighted = 0;
        long sentimentWeight = 0;
        double viewerSum = 0;
        int viewerSessions = 0;
        Double logo = null;
        Double reads = null;
        long commandUses = 0;
        long linkPosts = 0;
        int live = 0;
        for (SessionSummary report : reports) {
            StreamSession session = report.session();
            if (session.live()) {
                live++;
            }
            streamedMs += Math.max(0, session.durationMs());
            onScreenMs += report.onScreenMs();
            mentions += report.mentions();
            chat += report.chatMentions();
            voice += report.voiceMentions();
            if (report.mentionSentiment() != null && report.mentions() > 0) {
                sentimentWeighted += report.mentionSentiment() * report.mentions();
                sentimentWeight += report.mentions();
            }
            if (report.averageViewers() != null) {
                viewerSum += report.averageViewers();
                viewerSessions++;
            }
            if (report.value() != null) {
                logo = add(logo, report.value().logoValue());
                reads = add(reads, report.value().hostReadValue());
            }
            if (report.response() != null) {
                commandUses += report.response().commandUses();
                linkPosts += report.response().linkPosts();
            }
        }
        Double media = logo == null && reads == null ? null : (logo == null ? 0 : logo) + (reads == null ? 0 : reads);
        return new DealSummary.Totals(
                reports.size(),
                live,
                streamedMs,
                onScreenMs,
                streamedMs == 0 ? null : round((double) onScreenMs / streamedMs),
                mentions,
                chat,
                voice,
                sentimentWeight == 0 ? null : round(sentimentWeighted / sentimentWeight),
                viewerSessions == 0 ? null : round(viewerSum / viewerSessions),
                logo == null ? null : round(logo),
                reads == null ? null : round(reads),
                media == null ? null : round(media),
                commandUses,
                linkPosts);
    }

    private static Double add(Double total, Double value) {
        if (value == null) {
            return total;
        }
        return total == null ? value : total + value;
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    private static Deal toApi(DealRow row, long now) {
        return new Deal(
                row.id(),
                row.streamer(),
                row.sponsor(),
                row.startsAt(),
                row.endsAt(),
                row.promisedStreams(),
                row.fee(),
                row.currency(),
                row.cpmPer30sEquivalent(),
                row.hostReadRatePer1000(),
                row.trackedLink(),
                trackedLinkHost(row.trackedLink()),
                row.chatCommand(),
                row.channelPointReward(),
                row.covers(now),
                row.shareToken(),
                row.createdAt());
    }

    /** The host of an absolute http(s) link, lower-cased without a leading www; null otherwise. */
    static String trackedLinkHost(String link) {
        String cleaned = clean(link);
        if (cleaned == null) {
            return null;
        }
        try {
            URI uri = new URI(cleaned);
            String scheme = uri.getScheme();
            if (uri.getHost() == null || scheme == null) {
                return null;
            }
            if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
                return null;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    static String normalizeCommand(String command) {
        String cleaned = clean(command);
        if (cleaned == null) {
            return null;
        }
        String withBang = cleaned.startsWith("!") ? cleaned : "!" + cleaned;
        if (!COMMAND.matcher(withBang).matches()) {
            throw new IllegalArgumentException("chatCommand must be like !brand (letters, digits, _ or -)");
        }
        return withBang.toLowerCase(Locale.ROOT);
    }

    private static String normalizeLogin(String streamer) {
        String cleaned = clean(streamer);
        if (cleaned == null) {
            return null;
        }
        String login = cleaned.replaceFirst("^[@#]+", "").toLowerCase(Locale.ROOT);
        return login.isEmpty() ? null : login;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
