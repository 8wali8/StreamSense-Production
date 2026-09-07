package com.streamsense.chatservice.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.streamsense.chatservice.service.ChatEventIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChatIngestController.class)
class ChatIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatEventIngestService ingestService;

    @Test
    void ingest_missingMessage_returns4xx() throws Exception {
        String body =
                """
                                {
                                  "streamer": "test",
                                  "user": "u1",
                                  "timestamp": 1710000000000
                                }
                                """;

        mockMvc.perform(post("/api/chat/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());

        verify(ingestService, never()).ingestSynthetic(any(), any(), any());
    }

    @Test
    void ingest_validPayload_returns2xxAndEventId() throws Exception {
        when(ingestService.ingestSynthetic(any(), any(), any())).thenReturn("event-1");

        String body =
                """
                                {
                                  "streamer": "test",
                                  "user": "u1",
                                  "message": "hello",
                                  "timestamp": 1710000000000
                                }
                                """;

        mockMvc.perform(post("/api/chat/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.eventId", notNullValue()));

        verify(ingestService).ingestSynthetic(any(), any(), any());
    }
}
