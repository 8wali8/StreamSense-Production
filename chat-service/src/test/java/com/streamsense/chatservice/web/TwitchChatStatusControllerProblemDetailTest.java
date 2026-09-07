package com.streamsense.chatservice.web;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.streamsense.chatservice.controller.TwitchChatStatusController;
import com.streamsense.chatservice.twitch.TwitchChatLifecycleService;
import com.streamsense.chatservice.twitch.TwitchChatMetrics;

/** The channel-switch endpoint's 400 and 409 now come from the advice, as problem+json. */
class TwitchChatStatusControllerProblemDetailTest {

    private TwitchChatLifecycleService lifecycleService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(TwitchChatLifecycleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TwitchChatStatusController(mock(TwitchChatMetrics.class), lifecycleService))
                .setControllerAdvice(new GlobalExceptionHandler("chat-service"))
                .build();
    }

    @Test
    void emptyChannelListIsA400Problem() throws Exception {
        when(lifecycleService.switchChannels(anyList())).thenThrow(new IllegalArgumentException("at least one Twitch channel is required"));

        mockMvc.perform(post("/api/chat/twitch/channels").contentType("application/json").content("{\"channels\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://streamsense.dev/problems/invalid-request"))
                .andExpect(jsonPath("$.detail").value("at least one Twitch channel is required"))
                .andExpect(jsonPath("$.service").value("chat-service"));
    }

    @Test
    void disabledIngestionIsA409Problem() throws Exception {
        when(lifecycleService.switchChannels(anyList())).thenThrow(new IllegalStateException("Twitch chat ingestion is disabled"));

        mockMvc.perform(post("/api/chat/twitch/channels").contentType("application/json").content("{\"channels\":[\"austincs\"]}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://streamsense.dev/problems/conflict"))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Twitch chat ingestion is disabled"));
    }
}
