package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.api.SessionSummary;
import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.api.SummaryOptions;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.service.MetricAggregationService;
import com.streamsense.analyticsservice.service.SessionSummaryService;
import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.HelixRateLimitedException;
import com.streamsense.analyticsservice.twitch.HelixVideo;
import com.streamsense.analyticsservice.twitch.TwitchHelixClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** A recording becomes a closed session with Twitch's bounds, and replayed events land in it instead of opening a new one. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-vod-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.low-data-minimum-events=1",
            "streamsense.analytics.capture-session-close-check-ms=3600000"
        })
class VodImportTest {

    private static final String STREAMER = "importer";

    @MockitoBean
    private TwitchHelixClient helix;

    @Autowired
    private MetricAggregationService aggregation;

    @Autowired
    private StreamSessionService sessions;

    @Autowired
    private SessionSummaryService summaries;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void importsARecordingAsAClosedSessionThatReplayedEventsJoin() throws Exception {
        long createdAt = 1788631200000L;
        HelixVideo video = new HelixVideo(
                "2750461300",
                null,
                STREAMER,
                "Monza",
                createdAt,
                7_200_000L,
                "https://www.twitch.tv/videos/2750461300",
                900);
        when(helix.archives(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(video));
        when(helix.video("2750461300")).thenReturn(Optional.of(video));

        mockMvc.perform(get("/api/analytics/streams/" + STREAMER + "/vods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vodId").value("2750461300"))
                .andExpect(jsonPath("$[0].sessionId").doesNotExist());

        // No replay services are configured in tests, so the import records the session and reports why.
        mockMvc.perform(post("/api/analytics/streams/" + STREAMER + "/vods/2750461300/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"averageViewers\": 850}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.session.source").value("VOD"))
                .andExpect(jsonPath("$.session.vodId").value("2750461300"))
                .andExpect(jsonPath("$.session.live").value(false))
                .andExpect(jsonPath("$.session.durationMs").value(7200000))
                .andExpect(jsonPath("$.session.averageViewers").value(850.0))
                .andExpect(jsonPath("$.streamSessionId").value(STREAMER + "-vod-2750461300"))
                .andExpect(jsonPath("$.chatReplayStarted").value(false))
                .andExpect(jsonPath("$.problems.length()").value(2));

        StreamSession imported = sessions.list(STREAMER, null, null, null).get(0);
        mockMvc.perform(get("/api/analytics/streams/" + STREAMER + "/vods"))
                .andExpect(jsonPath("$[0].sessionId").value(imported.id()));

        // A replayed chat line with the original timestamp joins the imported session; nothing new opens.
        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("vod-chat-1");
        event.setStreamer(STREAMER);
        event.setUser("viewer");
        event.setMessage("!redbull");
        event.setTimestamp(createdAt + 600_000L);
        event.setStreamSessionId(STREAMER + "-vod-2750461300");
        aggregation.aggregateChatMessage("m", event);
        assertThat(sessions.list(STREAMER, null, null, null)).hasSize(1);
        SessionSummary summary = summaries
                .summary(imported.id(), new SummaryOptions(null, "!redbull", null, null, null))
                .orElseThrow();
        assertThat(summary.response().commandUses()).isEqualTo(1);
        assertThat(summary.session().live()).isFalse();

        // Importing again reuses the session and its viewer sample.
        mockMvc.perform(post("/api/analytics/streams/" + STREAMER + "/vods/2750461300/import"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.session.id").value(imported.id()))
                .andExpect(jsonPath("$.session.viewerSamples").value(1));
        mockMvc.perform(post("/api/analytics/streams/" + STREAMER + "/vods/999/import"))
                .andExpect(status().isBadRequest());

        // A recording of a broadcast that was captured live is refused: its numbers are already in the reports.
        long liveStart = createdAt + 30 * 24 * 3_600_000L;
        ChatMessageEvent live = new ChatMessageEvent();
        live.setEventId("live-chat-1");
        live.setStreamer(STREAMER);
        live.setUser("viewer");
        live.setMessage("hello from the live stream");
        live.setTimestamp(liveStart + 300_000L);
        live.setStreamSessionId("capture-live-1");
        aggregation.aggregateChatMessage("m", live);
        HelixVideo recordingOfLive = new HelixVideo(
                "2750461301",
                null,
                STREAMER,
                "Spa",
                liveStart,
                3_600_000L,
                "https://www.twitch.tv/videos/2750461301",
                100);
        when(helix.video("2750461301")).thenReturn(Optional.of(recordingOfLive));
        mockMvc.perform(post("/api/analytics/streams/" + STREAMER + "/vods/2750461301/import"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("analyzed live")));
        assertThat(sessions.list(STREAMER, liveStart, null, null))
                .extracting(StreamSession::source)
                .containsExactly("CAPTURE");
    }

    @Test
    void aRateLimitedHelixAnswers503WithRetryAfterInsteadOf500() throws Exception {
        when(helix.archives(anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new HelixRateLimitedException(System.currentTimeMillis() + 20_000L));

        mockMvc.perform(get("/api/analytics/streams/" + STREAMER + "/vods"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.type").value("https://streamsense.dev/problems/twitch-rate-limited"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Twitch is rate limiting")));
    }
}
