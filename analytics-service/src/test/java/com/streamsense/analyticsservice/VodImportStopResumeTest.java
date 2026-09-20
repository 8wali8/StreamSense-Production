package com.streamsense.analyticsservice;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.imports.CaptureReplayClient;
import com.streamsense.analyticsservice.imports.ChatReplayClient;
import com.streamsense.analyticsservice.imports.ReplayStatus;
import com.streamsense.analyticsservice.twitch.HelixVideo;
import com.streamsense.analyticsservice.twitch.TwitchHelixClient;
import com.streamsense.analyticsservice.web.ChannelScopeFilter;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * analytics-service owns an import's state: it fans a stop out to both halves, reads STOPPING until each
 * confirms, records the offset the slower half reached, resumes both from there, and fails a half whose
 * service has forgotten the import rather than resuming it on its own.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-vod-stop-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.capture-session-close-check-ms=3600000",
            "streamsense.analytics.vod-import-refresh-ms=3600000"
        })
class VodImportStopResumeTest {

    private static final String STREAMER = "racer";
    private static final String VOD = "2750461300";
    private static final String BASE = "/api/analytics/streams/" + STREAMER + "/vods/";

    @MockitoBean
    private TwitchHelixClient helix;

    @MockitoBean
    private ChatReplayClient chat;

    @MockitoBean
    private CaptureReplayClient capture;

    @Autowired
    private MockMvc mockMvc;

    private void recordingExists() {
        HelixVideo video = new HelixVideo(
                VOD, null, STREAMER, "Monza", 1788631200000L, 7_200_000L, "https://www.twitch.tv/videos/" + VOD, 900);
        when(helix.video(VOD)).thenReturn(Optional.of(video));
    }

    private static ReplayStatus reported(String state, long offset) {
        return new ReplayStatus(state, offset, false, null);
    }

    @Test
    void aStopReachesBothHalvesAndAResumeContinuesFromTheSlowerOne() throws Exception {
        recordingExists();
        when(chat.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 900)));
        when(capture.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 600)));

        mockMvc.perform(post(BASE + VOD + "/import"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.chatReplayStarted").value(true))
                .andExpect(jsonPath("$.captureReplayStarted").value(true))
                .andExpect(jsonPath("$.status.state").value("QUEUED"))
                .andExpect(jsonPath("$.status.durationSeconds").value(7200));
        verify(chat).replay(eq(STREAMER), eq(VOD), anyLong(), anyString(), eq(0L));
        verify(capture).replay(eq(STREAMER), eq(VOD), anyString(), anyLong(), anyLong(), anyString(), eq(0L));

        // Reading the channel's imports brings the active one up to date: the label follows the slower half.
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].vodId").value(VOD))
                .andExpect(jsonPath("$[0].state").value("IMPORTING"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(600))
                .andExpect(jsonPath("$[0].sessionId").isNumber());
        // Importing again while it runs is a conflict, not a second replay.
        mockMvc.perform(post(BASE + VOD + "/import")).andExpect(status().isConflict());

        // Stop: chat confirms at once, capture is still winding down an ffmpeg run.
        when(chat.stop(VOD)).thenReturn(Optional.of(reported("STOPPED", 960)));
        when(capture.stop(VOD)).thenReturn(Optional.of(new ReplayStatus("RUNNING", 610, true, null)));
        mockMvc.perform(post(BASE + VOD + "/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STOPPING"))
                .andExpect(jsonPath("$.chatState").value("STOPPED"))
                .andExpect(jsonPath("$.captureState").value("RUNNING"));
        verify(chat).stop(VOD);
        verify(capture).stop(VOD);

        // The capture half confirms on the next refresh; the import is stopped where the slower half got to.
        when(capture.status(VOD)).thenReturn(Optional.of(new ReplayStatus("STOPPED", 620, true, null)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("STOPPED"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(620));
        // Stopping again changes nothing and answers the same status.
        mockMvc.perform(post(BASE + VOD + "/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("STOPPED"));

        // Resume: the existing import action, and both halves are asked to continue from 620 s.
        mockMvc.perform(post(BASE + VOD + "/import"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status.state").value("QUEUED"))
                .andExpect(jsonPath("$.status.offsetSeconds").value(620));
        verify(chat).replay(eq(STREAMER), eq(VOD), anyLong(), anyString(), eq(620L));
        verify(capture).replay(eq(STREAMER), eq(VOD), anyString(), anyLong(), anyLong(), anyString(), eq(620L));

        // Both halves finish: the import is done, at the recording's full length.
        when(chat.status(VOD)).thenReturn(Optional.of(reported("DONE", 7100)));
        when(capture.status(VOD)).thenReturn(Optional.of(reported("DONE", 7200)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("DONE"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(7200));
    }

    @Test
    void aHalfItsServiceHasForgottenFailsInsteadOfResumingOnItsOwn() throws Exception {
        recordingExists();
        mockMvc.perform(post(BASE + VOD + "/import")).andExpect(status().isAccepted());

        // video-capture-service restarted and knows nothing of the import; chat-service is still at it.
        when(chat.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 300)));
        when(capture.status(VOD)).thenReturn(Optional.empty());
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("IMPORTING"))
                .andExpect(jsonPath("$[0].captureState").value("FAILED"))
                .andExpect(jsonPath("$[0].lastError").value(org.hamcrest.Matchers.containsString("lost track")));
        // Once chat finishes too, the import as a whole is failed at the offset the lost half had reached (0 s).
        when(chat.status(VOD)).thenReturn(Optional.of(reported("DONE", 7100)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("FAILED"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(0));
        verify(capture, never()).stop(anyString());

        // Nothing was ever imported for another recording: a stop of it is a client error, not a crash.
        mockMvc.perform(post(BASE + "999/stop")).andExpect(status().isBadRequest());
    }

    @Test
    void aStreamerReachesOnlyTheirOwnChannelsImports() throws Exception {
        mockMvc.perform(get("/api/analytics/streams/pokimane/vods/imports")
                        .header(ChannelScopeFilter.ROLE_HEADER, "streamer")
                        .header(ChannelScopeFilter.LOGIN_HEADER, "ninja"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("channel_forbidden"));
        mockMvc.perform(post("/api/analytics/streams/pokimane/vods/" + VOD + "/stop")
                        .header(ChannelScopeFilter.ROLE_HEADER, "streamer")
                        .header(ChannelScopeFilter.LOGIN_HEADER, "ninja"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analytics/streams/ninja/vods/imports")
                        .header(ChannelScopeFilter.ROLE_HEADER, "streamer")
                        .header(ChannelScopeFilter.LOGIN_HEADER, "ninja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
