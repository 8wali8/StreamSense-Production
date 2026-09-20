package com.streamsense.analyticsservice;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.api.DealCreateRequest;
import com.streamsense.analyticsservice.api.DealSummary;
import com.streamsense.analyticsservice.api.DealUpdateRequest;
import com.streamsense.analyticsservice.api.SessionSummary;
import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.SummaryOptions;
import com.streamsense.analyticsservice.config.StreamSenseProperties;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.persistence.DealRepository;
import com.streamsense.analyticsservice.relevance.SponsorRelevancePointer;
import com.streamsense.analyticsservice.service.DealService;
import com.streamsense.analyticsservice.service.MetricAggregationService;
import com.streamsense.analyticsservice.service.SessionSummaryService;
import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.HelixStream;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A deal is created over REST, its sessions roll up into its summary, and a session inside it inherits its terms. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-deals-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.low-data-minimum-events=1",
            "streamsense.analytics.capture-session-close-check-ms=3600000",
            "streamsense.analytics.value.cpm-per-30s-equivalent=10.0",
            "streamsense.analytics.value.host-read-rate-per-1000=20.0"
        })
class DealsTest {

    private static final String STREAMER = "dealer";

    @Autowired
    private MetricAggregationService aggregation;

    @Autowired
    private StreamSessionService sessions;

    @Autowired
    private SessionSummaryService summaries;

    @Autowired
    private DealService deals;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private StreamSenseProperties properties;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aDealRollsUpTheSessionsInsideItsDatesAndLendsItsTermsToTheirReports() throws Exception {
        long now = System.currentTimeMillis();
        long dealStart = now - 3 * 24 * 3_600_000L;
        long sessionStart = Math.floorDiv(now - 30 * 60_000L, 60_000L) * 60_000L;

        String created = mockMvc.perform(post("/api/analytics/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"streamer": "@Dealer", "sponsor": "Red Bull", "startsAt": %d, "promisedStreams": 4,
                                 "fee": 2500, "cpmPer30sEquivalent": 8, "trackedLink": "https://www.redbull.com/f1",
                                 "chatCommand": "redbull"}
                                """
                                        .formatted(dealStart)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.streamer").value(STREAMER))
                .andExpect(jsonPath("$.chatCommand").value("!redbull"))
                .andExpect(jsonPath("$.trackedLinkHost").value("redbull.com"))
                .andExpect(jsonPath("$.hostReadRatePer1000").value(20.0))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long dealId = Long.parseLong(created.replaceAll("^\\{\"id\":(\\d+).*$", "$1"));

        // A session inside the deal with two viewer samples, a command use, and a stream from before the deal.
        sessions.recordHelixLive(new HelixStream("51", "Dealer", "Monza", "F1", 1000, sessionStart));
        sessions.recordHelixLive(new HelixStream("51", "Dealer", "Monza", "F1", 3000, sessionStart));
        sessions.recordHelixLive(new HelixStream("50", "Dealer", "Old", "F1", 500, dealStart - 3_600_000L));
        aggregation.aggregateChatMessage("m", message("m1", sessionStart + 60_000L, "alice", "!redbull"));
        StreamSession inside = sessions.list(STREAMER, sessionStart, null, null).get(0);

        // The session report picks the deal up: its command counts and its CPM prices the exposure.
        SessionSummary report = summaries
                .summary(inside.id(), new SummaryOptions(null, null, null, null, null))
                .orElseThrow();
        assertThat(report.dealId()).isEqualTo(dealId);
        assertThat(report.sponsor()).isEqualTo("Red Bull");
        assertThat(report.response().chatCommand()).isEqualTo("!redbull");
        assertThat(report.response().commandUses()).isEqualTo(1);
        assertThat(report.value().cpmPer30sEquivalent()).isEqualTo(8.0);

        DealSummary summary = deals.summary(dealId).orElseThrow();
        assertThat(summary.deal().fee()).isEqualTo(2500.0);
        assertThat(summary.sessions()).extracting(s -> s.session().id()).containsExactly(inside.id());
        assertThat(summary.totals().streams()).isEqualTo(1);
        assertThat(summary.totals().commandUses()).isEqualTo(1);
        assertThat(summary.totals().averageViewers()).isEqualTo(2000.0);

        mockMvc.perform(get("/api/analytics/deals").param("streamer", STREAMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(dealId));
        mockMvc.perform(get("/api/analytics/deals/" + dealId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.streams").value(1))
                .andExpect(jsonPath("$.sessions[0].dealId").value(dealId));
        mockMvc.perform(get("/api/analytics/deals/999999")).andExpect(status().isNotFound());

        // Sharing mints one token that stays stable, resolves to the deal, and stops resolving once revoked.
        String token = deals.share(dealId).orElseThrow().token();
        assertThat(deals.share(dealId).orElseThrow().token()).isEqualTo(token);
        assertThat(token).hasSizeGreaterThanOrEqualTo(32);
        mockMvc.perform(get("/api/analytics/share/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dealId))
                .andExpect(jsonPath("$.shareToken").value(token));
        mockMvc.perform(delete("/api/analytics/deals/" + dealId + "/share")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/analytics/share/" + token)).andExpect(status().isNotFound());
        assertThat(deals.get(dealId).orElseThrow().shareToken()).isNull();

        // Bad input is a 400 problem: a command with spaces, an end before the start, a relative link.
        mockMvc.perform(post("/api/analytics/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamer\": \"x\", \"sponsor\": \"Y\", \"startsAt\": 10, \"endsAt\": 5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post("/api/analytics/deals")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"streamer\": \"x\", \"sponsor\": \"Y\", \"startsAt\": 10, \"trackedLink\": \"redbull.com\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aScheduledDealPointsRelevanceAtItsSponsorWhenItBegins() {
        long created = 1_800_000_000_000L;
        long startsAt = created + 3_600_000L;
        SponsorRelevancePointer pointer = mock(SponsorRelevancePointer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SponsorRelevancePointer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(pointer);

        // Created an hour before it starts: the periodic check leaves this deal alone. Other tests' deals in
        // the shared database may be running, so the count is not asserted, only this streamer's pointer.
        DealService beforeStart = new DealService(
                dealRepository,
                sessions,
                summaries,
                properties,
                provider,
                Clock.fixed(Instant.ofEpochMilli(created), UTC));
        beforeStart.create(new DealCreateRequest(
                "scheduled",
                "Red Bull",
                startsAt,
                startsAt + 86_400_000L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        beforeStart.activateStartedDeals();
        verify(pointer, never()).point(eq("scheduled"), anyString());

        // Once the clock passes the start, the periodic check points relevance exactly once and then rests.
        DealService afterStart = new DealService(
                dealRepository,
                sessions,
                summaries,
                properties,
                provider,
                Clock.fixed(Instant.ofEpochMilli(startsAt + 60_000L), UTC));
        assertThat(afterStart.activateStartedDeals()).isGreaterThanOrEqualTo(1);
        assertThat(afterStart.activateStartedDeals()).isZero();
        verify(pointer, times(1)).point("scheduled", "Red Bull");
    }

    @Test
    void anOlderOverlappingDealIsPointedAtAgainWhenTheNewerOneEnds() {
        long t0 = 1_810_000_000_000L;
        long hour = 3_600_000L;
        SponsorRelevancePointer pointer = mock(SponsorRelevancePointer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SponsorRelevancePointer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(pointer);
        SteppingClock clock = new SteppingClock(t0);
        DealService service = new DealService(dealRepository, sessions, summaries, properties, provider, clock);

        // A long deal with Logitech, then a one-hour deal with Red Bull created inside it: each is pointed at as it
        // becomes current.
        service.create(deal("overlap", "Logitech", t0, t0 + 72 * hour));
        clock.millis = t0 + hour;
        service.create(deal("overlap", "Red Bull", t0 + hour, t0 + 2 * hour));
        verify(pointer, times(1)).point("overlap", "Logitech");
        verify(pointer, times(1)).point("overlap", "Red Bull");

        // While Red Bull runs the check has nothing to do for this streamer.
        clock.millis = t0 + 90 * 60_000L;
        service.activateStartedDeals();
        verify(pointer, times(1)).point("overlap", "Red Bull");

        // Once Red Bull ends, Logitech is current again and is pointed at once more; then the check rests.
        clock.millis = t0 + 3 * hour;
        service.activateStartedDeals();
        verify(pointer, times(2)).point("overlap", "Logitech");
        service.activateStartedDeals();
        verify(pointer, times(2)).point("overlap", "Logitech");
        verify(pointer, times(1)).point("overlap", "Red Bull");
    }

    @Test
    void aDealIsEditedAsAWholeEndedByItsEndDateAndDeletedOnlyOnceUnshared() throws Exception {
        long now = System.currentTimeMillis();
        long dealStart = now - 2 * 24 * 3_600_000L;
        long sessionStart = Math.floorDiv(now - 20 * 60_000L, 60_000L) * 60_000L;
        String created = mockMvc.perform(post("/api/analytics/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"streamer": "editor", "sponsor": "Logitech", "startsAt": %d, "fee": 1000,
                                 "cpmPer30sEquivalent": 8, "chatCommand": "logi", "trackedLink": "https://logitech.com/g"}
                                """
                                        .formatted(dealStart)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long dealId = Long.parseLong(created.replaceAll("^\\{\"id\":(\\d+).*$", "$1"));
        sessions.recordHelixLive(new HelixStream("61", "editor", "Ranked", "Valorant", 400, sessionStart));

        // The whole deal is sent back: the sponsor and CPM change, the command and link are dropped, the rate
        // left out returns to the configured default, and the report inside re-prices on its next read.
        mockMvc.perform(put("/api/analytics/deals/" + dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"sponsor": "Red Bull", "startsAt": %d, "promisedStreams": 2, "fee": 1500,
                                 "cpmPer30sEquivalent": 12}
                                """
                                        .formatted(dealStart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dealId))
                .andExpect(jsonPath("$.streamer").value("editor"))
                .andExpect(jsonPath("$.sponsor").value("Red Bull"))
                .andExpect(jsonPath("$.promisedStreams").value(2))
                .andExpect(jsonPath("$.chatCommand").isEmpty())
                .andExpect(jsonPath("$.trackedLinkHost").isEmpty())
                .andExpect(jsonPath("$.hostReadRatePer1000").value(20.0))
                .andExpect(jsonPath("$.active").value(true));
        DealSummary summary = deals.summary(dealId).orElseThrow();
        assertThat(summary.deal().fee()).isEqualTo(1500.0);
        assertThat(summary.sessions()).hasSize(1);
        assertThat(summary.sessions().get(0).sponsor()).isEqualTo("Red Bull");
        assertThat(summary.sessions().get(0).value().cpmPer30sEquivalent()).isEqualTo(12.0);

        // Ending it is an update whose end is now: the deal is no longer active and the session, which started
        // before the end, is still inside it.
        long endsAt = System.currentTimeMillis();
        mockMvc.perform(put("/api/analytics/deals/" + dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sponsor\": \"Red Bull\", \"startsAt\": %d, \"endsAt\": %d}"
                                .formatted(dealStart, endsAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.endsAt").value(endsAt));
        assertThat(deals.summary(dealId).orElseThrow().sessions()).hasSize(1);

        // Bad input and unknown deals answer as creation does.
        mockMvc.perform(put("/api/analytics/deals/" + dealId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sponsor\": \"Red Bull\", \"startsAt\": 10, \"endsAt\": 5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/analytics/deals/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sponsor\": \"Red Bull\", \"startsAt\": 10}"))
                .andExpect(status().isNotFound());

        // A shared deal cannot be deleted; revoked, it can, and then it is gone.
        deals.share(dealId).orElseThrow();
        mockMvc.perform(delete("/api/analytics/deals/" + dealId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("revoke the deal's share link before deleting it"));
        mockMvc.perform(delete("/api/analytics/deals/" + dealId + "/share")).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/analytics/deals/" + dealId)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/analytics/deals/" + dealId)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/analytics/deals/" + dealId)).andExpect(status().isNotFound());
    }

    @Test
    void anEditPointsRelevanceAtTheChannelsCurrentDealAgain() {
        long t0 = 1_820_000_000_000L;
        long hour = 3_600_000L;
        SponsorRelevancePointer pointer = mock(SponsorRelevancePointer.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SponsorRelevancePointer> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(pointer);
        SteppingClock clock = new SteppingClock(t0 + hour);
        DealService service = new DealService(dealRepository, sessions, summaries, properties, provider, clock);

        // A running deal is pointed at on creation; renaming its sponsor points at the new name once.
        long id =
                service.create(deal("editable", "Logitech", t0, t0 + 48 * hour)).id();
        verify(pointer, times(1)).point("editable", "Logitech");
        service.update(id, terms("Red Bull", t0, t0 + 48 * hour));
        verify(pointer, times(1)).point("editable", "Red Bull");

        // Ended early, no deal covers now: relevance stays where it was, and the periodic check has nothing to do.
        service.update(id, terms("Red Bull", t0, t0 + hour));
        service.activateStartedDeals();
        verify(pointer, times(1)).point("editable", "Red Bull");
        verify(pointer, times(1)).point("editable", "Logitech");

        // An older deal that the edit uncovers is pointed at again, and deleting the edited deal changes nothing.
        service.create(deal("editable", "Razer", t0 - hour, t0 + 72 * hour));
        verify(pointer, times(1)).point("editable", "Razer");
        assertThat(service.delete(id)).isTrue();
        verify(pointer, times(1)).point("editable", "Razer");
        assertThat(service.get(id)).isEmpty();
    }

    private static DealUpdateRequest terms(String sponsor, long startsAt, long endsAt) {
        return new DealUpdateRequest(sponsor, startsAt, endsAt, null, null, null, null, null, null, null, null);
    }

    private static DealCreateRequest deal(String streamer, String sponsor, long startsAt, long endsAt) {
        return new DealCreateRequest(
                streamer, sponsor, startsAt, endsAt, null, null, null, null, null, null, null, null);
    }

    /** A clock the test moves by hand. */
    private static final class SteppingClock extends Clock {
        long millis;

        SteppingClock(long millis) {
            this.millis = millis;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public java.time.ZoneId getZone() {
            return UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private static ChatMessageEvent message(String id, long at, String user, String text) {
        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId(id);
        event.setStreamer(STREAMER);
        event.setUser(user);
        event.setMessage(text);
        event.setTimestamp(at);
        return event;
    }
}
