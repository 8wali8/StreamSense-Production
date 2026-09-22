package com.streamsense.analyticsservice;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
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

    @MockitoBean
    private TwitchHelixClient helix;

    @MockitoBean
    private ChatReplayClient chat;

    @MockitoBean
    private CaptureReplayClient capture;

    @Autowired
    private MockMvc mockMvc;

    /** Each test has its own channel and recording: the rows live in one database, and a channel's list is per streamer. */
    private void recordingExists(String streamer, String vod) {
        HelixVideo video = new HelixVideo(
                vod, null, streamer, "Monza", 1788631200000L, 7_200_000L, "https://www.twitch.tv/videos/" + vod, 900);
        when(helix.video(vod)).thenReturn(Optional.of(video));
    }

    private static String base(String streamer) {
        return "/api/analytics/streams/" + streamer + "/vods/";
    }

    /** A streamer hands over a small chat log for the recording, so the import has a chat half. */
    private void logSupplied(String base, String vod) throws Exception {
        mockMvc.perform(
                        put(base + vod + "/chat-log")
                                .param("fileName", "chat.json")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(
                                        "[{\"offsetSeconds\": 10, \"user\": \"alice\", \"message\": \"hi\"}, {\"offsetSeconds\": 900, \"user\": \"bob\", \"message\": \"!redbull\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lineCount").value(2))
                .andExpect(jsonPath("$.fileName").value("chat.json"));
    }

    private static ReplayStatus reported(String state, long offset) {
        return new ReplayStatus(state, offset, false, null);
    }

    @Test
    void aStopReachesBothHalvesAndAResumeContinuesFromTheSlowerOne() throws Exception {
        String streamer = "racer1";
        String VOD = "2750461300";
        String BASE = base(streamer);
        recordingExists(streamer, VOD);
        logSupplied(BASE, VOD);
        when(chat.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 900)));
        when(capture.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 600)));

        mockMvc.perform(post(BASE + VOD + "/import"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.chatReplayStarted").value(true))
                .andExpect(jsonPath("$.captureReplayStarted").value(true))
                .andExpect(jsonPath("$.status.state").value("QUEUED"))
                .andExpect(jsonPath("$.status.durationSeconds").value(7200));
        verify(chat).replay(eq(streamer), eq(VOD), anyLong(), anyString(), eq(0L), anyList());
        // The transcript stride is pinned in analytics config (10 s, the live segment length) and always sent.
        verify(capture).replay(eq(streamer), eq(VOD), anyString(), anyLong(), anyLong(), anyString(), eq(0L), eq(10));

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

        // Resume: the existing import action, and each half is asked to continue from where it got to.
        mockMvc.perform(post(BASE + VOD + "/import"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status.state").value("QUEUED"))
                .andExpect(jsonPath("$.status.offsetSeconds").value(620));
        verify(chat).replay(eq(streamer), eq(VOD), anyLong(), anyString(), eq(960L), anyList());
        verify(capture).replay(eq(streamer), eq(VOD), anyString(), anyLong(), anyLong(), anyString(), eq(620L), eq(10));

        // Both halves finish: the import is done, at the recording's full length.
        when(chat.status(VOD)).thenReturn(Optional.of(reported("DONE", 7100)));
        when(capture.status(VOD)).thenReturn(Optional.of(reported("DONE", 7200)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("DONE"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(7200));
    }

    @Test
    void aHalfItsServiceHasForgottenFailsInsteadOfResumingOnItsOwn() throws Exception {
        String streamer = "racer2";
        String VOD = "2750461301";
        String BASE = base(streamer);
        recordingExists(streamer, VOD);
        logSupplied(BASE, VOD);
        mockMvc.perform(post(BASE + VOD + "/import")).andExpect(status().isAccepted());

        // video-capture-service restarted and knows nothing of the import; chat-service is still at it.
        when(chat.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 300)));
        when(capture.status(VOD)).thenReturn(Optional.empty());
        // The offset follows the half still working; the lost half is named by its state and the error.
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("IMPORTING"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(300))
                .andExpect(jsonPath("$[0].captureState").value("FAILED"))
                .andExpect(jsonPath("$[0].lastError").value(org.hamcrest.Matchers.containsString("lost track")));
        // Once chat finishes too, the import as a whole is failed; the chat half reached the end.
        when(chat.status(VOD)).thenReturn(Optional.of(reported("DONE", 7100)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("FAILED"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(7200));
        verify(capture, never()).stop(anyString());

        // Nothing was ever imported for another recording: a stop of it is a client error, not a crash.
        mockMvc.perform(post(BASE + "999/stop")).andExpect(status().isBadRequest());
    }

    @Test
    void aFailedHalfDoesNotPinTheOtherHalfsProgressOrItsResume() throws Exception {
        String streamer = "racer3";
        String VOD = "2750461302";
        String BASE = base(streamer);
        recordingExists(streamer, VOD);
        logSupplied(BASE, VOD);
        mockMvc.perform(post(BASE + VOD + "/import")).andExpect(status().isAccepted());

        // The chat half failed at once (Twitch refused it); the capture half works on. The label follows
        // the half still in play, not the one that fell over at 0 s.
        when(chat.status(VOD)).thenReturn(Optional.of(new ReplayStatus("FAILED", 0, false, "refused")));
        when(capture.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 371)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("IMPORTING"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(371));

        // Stopped there; a resume sends the capture half back to 371 s and the chat half to its own 0 s.
        when(chat.stop(VOD)).thenReturn(Optional.of(new ReplayStatus("FAILED", 0, false, "refused")));
        when(capture.stop(VOD)).thenReturn(Optional.of(new ReplayStatus("STOPPED", 380, true, null)));
        mockMvc.perform(post(BASE + VOD + "/stop"))
                .andExpect(jsonPath("$.state").value("STOPPED"))
                .andExpect(jsonPath("$.offsetSeconds").value(380));
        mockMvc.perform(post(BASE + VOD + "/import")).andExpect(status().isAccepted());
        // The chat half is asked from 0 s both times (the first import and the resume); capture from 380 s.
        verify(chat, org.mockito.Mockito.times(2))
                .replay(eq(streamer), eq(VOD), anyLong(), anyString(), eq(0L), anyList());
        verify(capture).replay(eq(streamer), eq(VOD), anyString(), anyLong(), anyLong(), anyString(), eq(380L), eq(10));
    }

    @Test
    void withoutALogTheImportHasNoChatHalfAndALogSuppliedLaterFillsItIn() throws Exception {
        String streamer = "racer4";
        String VOD = "2750461304";
        String BASE = base(streamer);
        recordingExists(streamer, VOD);

        // No log: the chat service is never asked, nothing is reported as a problem, and the row says NONE.
        mockMvc.perform(post(BASE + VOD + "/import"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.chatReplayStarted").value(false))
                .andExpect(jsonPath("$.problems.length()").value(0))
                .andExpect(jsonPath("$.status.state").value("QUEUED"))
                .andExpect(jsonPath("$.status.chatState").value("NONE"));
        verify(chat, never()).replay(anyString(), anyString(), anyLong(), anyString(), anyLong(), anyList());

        // The capture half alone carries the import.
        when(capture.status(VOD)).thenReturn(Optional.of(reported("RUNNING", 600)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("IMPORTING"))
                .andExpect(jsonPath("$[0].offsetSeconds").value(600));

        // A log handed over while the capture half runs starts the chat half at once.
        logSupplied(BASE, VOD);
        verify(chat).replay(eq(streamer), eq(VOD), anyLong(), anyString(), eq(0L), anyList());
        when(chat.status(VOD)).thenReturn(Optional.of(reported("DONE", 900)));
        when(capture.status(VOD)).thenReturn(Optional.of(reported("DONE", 7200)));
        mockMvc.perform(get(BASE + "imports"))
                .andExpect(jsonPath("$[0].state").value("DONE"))
                .andExpect(jsonPath("$[0].chatState").value("DONE"));

        // The recordings list names the log; a log that cannot be read, or an unknown zone, is refused.
        when(helix.archives(eq(streamer), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(java.util.List.of(new HelixVideo(
                        VOD,
                        null,
                        streamer,
                        "Monza",
                        1788631200000L,
                        7_200_000L,
                        "https://www.twitch.tv/videos/" + VOD,
                        900)));
        mockMvc.perform(get(BASE.substring(0, BASE.length() - 1)))
                .andExpect(jsonPath("$[0].chatLog.lineCount").value(2))
                .andExpect(jsonPath("$[0].chatLog.lastOffsetSeconds").value(900.0));
        mockMvc.perform(put(BASE + VOD + "/chat-log")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("no chat here"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no chat lines")));
        mockMvc.perform(put(BASE + VOD + "/chat-log")
                        .param("timezone", "Mars/Olympus")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("[00:10:00] a: b"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStreamerReachesOnlyTheirOwnChannelsImports() throws Exception {
        String VOD = "2750461309";
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
