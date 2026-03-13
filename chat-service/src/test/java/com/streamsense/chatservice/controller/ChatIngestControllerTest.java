package com.streamsense.chatservice.controller;

import com.streamsense.chatservice.kafka.ChatKafkaProducer;
import com.streamsense.chatservice.metrics.ChatMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatIngestController.class)
class ChatIngestControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private ChatKafkaProducer producer;

        @MockBean
        private ChatMetrics chatMetrics;

        @Test
        void ingest_missingMessage_returns4xx() throws Exception {
                String body = """
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

                verify(producer, never()).publish(any(), any(), any());
        }

        @Test
        void ingest_validPayload_returns2xxAndEventId() throws Exception {
                String body = """
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
                                .andExpect(jsonPath("$.eventId", notNullValue()));

                verify(producer).publish(any(), any(), any());
                verify(chatMetrics).incrementChatIngest();
        }
}