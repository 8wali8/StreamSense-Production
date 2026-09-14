package com.streamsense.chatservice.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.chatservice.twitch.TwitchChatLifecycleService;
import com.streamsense.chatservice.twitch.TwitchChatMetrics;
import com.streamsense.chatservice.twitch.TwitchChatState;
import com.streamsense.chatservice.twitch.TwitchChatStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TwitchChatStatusController.class)
class TwitchChatStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TwitchChatMetrics metrics;

    @MockitoBean
    private TwitchChatLifecycleService lifecycleService;

    @Test
    void put_joinsOneChannel() throws Exception {
        when(lifecycleService.joinChannel("ninja")).thenReturn(snapshot(List.of("ninja")));
        when(lifecycleService.isJoined("ninja")).thenReturn(true);

        mockMvc.perform(put("/api/chat/twitch/channels/ninja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("ninja"))
                .andExpect(jsonPath("$.joined").value(true));

        verify(lifecycleService).joinChannel("ninja");
    }

    @Test
    void delete_partsOneChannel() throws Exception {
        when(lifecycleService.partChannel("ninja")).thenReturn(snapshot(List.of()));
        when(lifecycleService.isJoined("ninja")).thenReturn(false);

        mockMvc.perform(delete("/api/chat/twitch/channels/ninja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joined").value(false));

        verify(lifecycleService).partChannel("ninja");
    }

    @Test
    void status_tellsAStreamerAboutTheirOwnChannelOnly() throws Exception {
        when(metrics.snapshot()).thenReturn(snapshot(List.of("ninja", "pokimane")));

        mockMvc.perform(get("/api/chat/twitch/status")
                        .header("X-StreamSense-Auth-Role", "streamer")
                        .header("X-StreamSense-Auth-Login", "Ninja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.length()").value(1))
                .andExpect(jsonPath("$.channels[0]").value("ninja"));
    }

    @Test
    void status_tellsAnOperatorAboutEveryChannel() throws Exception {
        when(metrics.snapshot()).thenReturn(snapshot(List.of("ninja", "pokimane")));

        mockMvc.perform(get("/api/chat/twitch/status")
                        .header("X-StreamSense-Auth-Role", "operator")
                        .header("X-StreamSense-Auth-Login", "ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.length()").value(2));
    }

    @Test
    void channelStatus_readsOneChannelWithoutNamingTheOthers() throws Exception {
        when(metrics.snapshot()).thenReturn(snapshot(List.of("ninja", "pokimane")));
        when(lifecycleService.isJoined(anyString())).thenReturn(true);

        mockMvc.perform(get("/api/chat/twitch/channels/@Ninja"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channel").value("ninja"))
                .andExpect(jsonPath("$.joined").value(true))
                .andExpect(jsonPath("$.channels").doesNotExist());
    }

    private static TwitchChatStatus snapshot(List<String> channels) {
        return new TwitchChatStatus(true, TwitchChatState.CONNECTED, channels, 0L, null, 0L);
    }
}
