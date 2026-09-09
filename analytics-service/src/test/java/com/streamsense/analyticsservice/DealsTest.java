package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.api.DealSummary;
import com.streamsense.analyticsservice.api.SessionSummary;
import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.SummaryOptions;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.service.DealService;
import com.streamsense.analyticsservice.service.MetricAggregationService;
import com.streamsense.analyticsservice.service.SessionSummaryService;
import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.HelixStream;
import org.junit.jupiter.api.Test;
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
