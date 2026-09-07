package com.streamsense.sentimentservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.streamsense.sentimentservice.dto.MlSentimentResponse;
import com.streamsense.sentimentservice.events.ChatMessageEvent;
import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;

/** The session fields on a chat event survive into the sentiment event that analytics buckets by. */
class SentimentServiceSessionFieldsTest {

    @Test
    void sessionFieldsPassThroughFromChatToSentiment() {
        ChatMessageEvent chat = new ChatMessageEvent();
        chat.setEventId("evt-1");
        chat.setStreamer("streamer-1");
        chat.setUser("user-1");
        chat.setMessage("hello");
        chat.setTimestamp(1710000000000L);
        chat.setSource("TWITCH_VOD_REPLAY");
        chat.setChannelLogin("streamer-1");
        chat.setStreamSessionId("streamer-1-1710000000000");
        chat.setTwitchStreamId("12345");
        MlSentimentResponse response = new MlSentimentResponse();
        response.setLabel("POSITIVE");
        response.setScore(0.5d);
        response.setModelVersion("lexical-v1");

        SentimentAnalysisEvent event = SentimentService.buildSentimentEvent(chat, response);

        assertThat(event.getSourceEventId()).isEqualTo("evt-1");
        assertThat(event.getSource()).isEqualTo("TWITCH_VOD_REPLAY");
        assertThat(event.getChannelLogin()).isEqualTo("streamer-1");
        assertThat(event.getStreamSessionId()).isEqualTo("streamer-1-1710000000000");
        assertThat(event.getTwitchStreamId()).isEqualTo("12345");
        assertThat(event.getLabel()).isEqualTo("POSITIVE");
    }
}
