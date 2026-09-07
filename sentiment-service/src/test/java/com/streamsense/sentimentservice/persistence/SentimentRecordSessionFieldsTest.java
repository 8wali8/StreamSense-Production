package com.streamsense.sentimentservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/** The session fields survive a round trip through the migrated table, so history matches the live stream. */
@DataJpaTest
class SentimentRecordSessionFieldsTest {

    @Autowired
    private SentimentRecordRepository repository;

    @Test
    void sessionFieldsArePersistedAndReadBack() {
        SentimentAnalysisEvent event = sample();
        event.setSource("TWITCH_VOD_REPLAY");
        event.setChannelLogin("redbull-testing");
        event.setStreamSessionId("redbull-testing-2750461300");
        event.setTwitchStreamId("2750461300");

        repository.saveAndFlush(SentimentRecordEntity.fromEvent(event));
        List<SentimentRecordEntity> stored =
                repository.findByStreamerOrderByChatTimestampDesc("redbull-testing", PageRequest.of(0, 5));

        assertThat(stored).hasSize(1);
        SentimentAnalysisEvent restored = stored.get(0).toEvent();
        assertThat(restored.getSource()).isEqualTo("TWITCH_VOD_REPLAY");
        assertThat(restored.getChannelLogin()).isEqualTo("redbull-testing");
        assertThat(restored.getStreamSessionId()).isEqualTo("redbull-testing-2750461300");
        assertThat(restored.getTwitchStreamId()).isEqualTo("2750461300");
    }

    @Test
    void rowsWithoutSessionFieldsStillLoad() {
        repository.saveAndFlush(SentimentRecordEntity.fromEvent(sample()));

        SentimentAnalysisEvent restored = repository
                .findByStreamerOrderByChatTimestampDesc("redbull-testing", PageRequest.of(0, 5))
                .get(0)
                .toEvent();

        assertThat(restored.getSource()).isNull();
        assertThat(restored.getStreamSessionId()).isNull();
        assertThat(restored.getLabel()).isEqualTo("POSITIVE");
    }

    private static SentimentAnalysisEvent sample() {
        SentimentAnalysisEvent event = new SentimentAnalysisEvent();
        event.setSentimentEventId("sent-" + System.nanoTime());
        event.setSourceEventId("evt-1");
        event.setStreamer("redbull-testing");
        event.setUser("u1");
        event.setMessage("great stream");
        event.setChatTimestamp(1710000000000L);
        event.setProcessedAt(1710000000500L);
        event.setLabel("POSITIVE");
        event.setScore(0.8d);
        event.setModelVersion("stub-v1");
        return event;
    }
}
