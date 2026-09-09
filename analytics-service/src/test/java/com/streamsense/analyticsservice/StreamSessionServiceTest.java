package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.api.StreamSession;
import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.HelixStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-sessions-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.capture-session-idle-close-minutes=10",
            "streamsense.analytics.capture-session-close-check-ms=3600000"
        })
class StreamSessionServiceTest {

    @Autowired
    private StreamSessionService sessions;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void captureEventsOpenExtendAndCloseASession() throws Exception {
        long now = System.currentTimeMillis();
        long start = now - 40 * 60_000L;
        sessions.recordActivity(
                "redbull-testing", "redbull-testing-2750461300", "2750461300", "redbull-testing", start);
        sessions.recordActivity("redbull-testing", "redbull-testing-2750461300", null, null, start + 20 * 60_000L);
        // A late event before the first one still counts: the session starts at the earliest event.
        sessions.recordActivity("redbull-testing", "redbull-testing-2750461300", null, null, start - 5_000L);

        List<StreamSession> open = sessions.list("redbull-testing", null, null, null);
        assertThat(open).hasSize(1);
        assertThat(open.get(0).source()).isEqualTo("CAPTURE");
        assertThat(open.get(0).live()).isTrue();
        assertThat(open.get(0).startedAt()).isEqualTo(start - 5_000L);
        assertThat(open.get(0).twitchStreamId()).isEqualTo("2750461300");

        // Last event was 20 minutes ago, past the 10 minute idle limit: the session closes at that event.
        // Other tests in this context may leave idle capture sessions too, so at least ours closes.
        assertThat(sessions.closeIdleCaptureSessions()).isGreaterThanOrEqualTo(1);
        StreamSession closed = sessions.get(open.get(0).id()).orElseThrow();
        assertThat(closed.live()).isFalse();
        assertThat(closed.endedAt()).isEqualTo(start + 20 * 60_000L);
        assertThat(closed.durationMs()).isEqualTo(20 * 60_000L + 5_000L);

        mockMvc.perform(get("/api/analytics/streams/redbull-testing/sessions").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(closed.id()))
                .andExpect(jsonPath("$[0].source").value("CAPTURE"));
        mockMvc.perform(get("/api/analytics/sessions/999999")).andExpect(status().isNotFound());
    }

    @Test
    void helixSessionsCarryViewersHideOverlappingCaptureAndCloseWhenNotLive() {
        long now = System.currentTimeMillis();
        long start = now - 30 * 60_000L;
        sessions.recordHelixLive(new HelixStream("41", "Racer", "Monza night", "Formula 1", 1200, start));
        sessions.recordHelixLive(new HelixStream("41", "Racer", "Monza night", "Formula 1", 1600, start));
        // A capture run during the same broadcast is the same stream; it must not appear twice.
        sessions.recordActivity("racer", "racer-capture-1", null, "racer", start + 60_000L);

        List<StreamSession> listed = sessions.list("racer", null, null, null);
        assertThat(listed).hasSize(1);
        StreamSession helix = listed.get(0);
        assertThat(helix.source()).isEqualTo("HELIX");
        assertThat(helix.title()).isEqualTo("Monza night");
        assertThat(helix.category()).isEqualTo("Formula 1");
        assertThat(helix.peakViewers()).isEqualTo(1600);
        assertThat(helix.averageViewers()).isEqualTo(1400.0d);
        assertThat(helix.viewerSamples()).isEqualTo(2);
        assertThat(helix.live()).isTrue();

        assertThat(sessions.closeHelixSessionsNotLive(List.of("racer"), List.of()))
                .isEqualTo(1);
        assertThat(sessions.get(helix.id()).orElseThrow().live()).isFalse();
        // An unwatched channel's session is left alone.
        assertThat(sessions.closeHelixSessionsNotLive(List.of("someone-else"), List.of()))
                .isZero();
    }
}
