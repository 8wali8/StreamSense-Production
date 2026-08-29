package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.events.ChatMessageEvent;
import com.streamsense.analyticsservice.service.MetricAggregationService;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {
            AnalyticsDeadLetterIntegrationTest.CHAT_TOPIC,
            AnalyticsDeadLetterIntegrationTest.CHAT_DLT,
            "stream.sentiment.events",
            "stream.sentiment.events.analytics.dlt",
            "stream.transcript.sentiment.events",
            "stream.transcript.sentiment.events.analytics.dlt",
            "stream.sponsor.detections",
            "stream.sponsor.detections.analytics.dlt"
        })
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
            "spring.datasource.url=jdbc:h2:mem:analytics-dlt-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.processing.retryBackoffMs=50",
            "streamsense.processing.maxRetries=2"
        })
class AnalyticsDeadLetterIntegrationTest {

    static final String CHAT_TOPIC = "stream.chat.messages";
    static final String CHAT_DLT = "stream.chat.messages.analytics.dlt";

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private MetricAggregationService aggregationService;

    private KafkaTemplate<String, String> producer;
    private Consumer<String, String> deadLetterConsumer;

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }

        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(broker), new StringSerializer(), new StringSerializer()));

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("analytics-dlt-test-" + System.nanoTime(), "true", broker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        deadLetterConsumer = new DefaultKafkaConsumerFactory<>(
                        consumerProps, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(deadLetterConsumer, CHAT_DLT);
    }

    @AfterEach
    void tearDown() {
        deadLetterConsumer.close();
        producer.destroy();
    }

    @Test
    void malformedPayload_isDeadLetteredAndDoesNotBlockLaterEvents() throws Exception {
        String malformed = "{\"eventId\":\"malformed-" + System.nanoTime() + "\", this is not json";
        producer.send(CHAT_TOPIC, "dlt-streamer", malformed).get();

        ConsumerRecord<String, String> dead = awaitDeadLetter(record -> malformed.equals(record.value()));
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(CHAT_TOPIC);
        verify(aggregationService, never()).aggregateChatMessage(anyString(), any());

        ChatMessageEvent good = chatEvent("good-after-malformed-" + System.nanoTime());
        producer.send(CHAT_TOPIC, good.getStreamer(), objectMapper.writeValueAsString(good))
                .get();
        awaitProcessed(good.getEventId());
    }

    @Test
    void invalidEvent_isDeadLetteredWithoutRetry() throws Exception {
        ChatMessageEvent invalid = chatEvent("invalid-" + System.nanoTime());
        invalid.setStreamer("   ");
        String payload = objectMapper.writeValueAsString(invalid);
        producer.send(CHAT_TOPIC, "dlt-streamer", payload).get();

        ConsumerRecord<String, String> dead =
                awaitDeadLetter(record -> record.value().contains(invalid.getEventId()));
        assertThat(dead.value()).isEqualTo(payload);
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(CHAT_TOPIC);
        verify(aggregationService, times(1)).aggregateChatMessage(anyString(), any());
        assertThat(processedCount(invalid.getEventId())).isZero();
    }

    @Test
    void transientFailure_isRetriedAndThenProcessed() throws Exception {
        ChatMessageEvent event = chatEvent("transient-" + System.nanoTime());
        doThrow(new DataAccessResourceFailureException("postgres unavailable"))
                .doThrow(new DataAccessResourceFailureException("postgres still unavailable"))
                .doCallRealMethod()
                .when(aggregationService)
                .aggregateChatMessage(anyString(), any());

        producer.send(CHAT_TOPIC, event.getStreamer(), objectMapper.writeValueAsString(event))
                .get();

        awaitProcessed(event.getEventId());
        verify(aggregationService, times(3)).aggregateChatMessage(anyString(), any());
        assertThat(pollDeadLetters(Duration.ofSeconds(2)))
                .noneMatch(record -> record.value().contains(event.getEventId()));
    }

    @Test
    void persistentFailure_isDeadLetteredAfterRetries() throws Exception {
        ChatMessageEvent event = chatEvent("persistent-" + System.nanoTime());
        doThrow(new DataAccessResourceFailureException("postgres unavailable"))
                .when(aggregationService)
                .aggregateChatMessage(anyString(), any());

        producer.send(CHAT_TOPIC, event.getStreamer(), objectMapper.writeValueAsString(event))
                .get();

        ConsumerRecord<String, String> dead =
                awaitDeadLetter(record -> record.value().contains(event.getEventId()));
        assertThat(header(dead, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(CHAT_TOPIC);
        verify(aggregationService, times(3)).aggregateChatMessage(anyString(), any());
        assertThat(processedCount(event.getEventId())).isZero();
    }

    private ConsumerRecord<String, String> awaitDeadLetter(Predicate<ConsumerRecord<String, String>> matcher) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            for (ConsumerRecord<String, String> record : deadLetterConsumer.poll(Duration.ofMillis(250))) {
                if (matcher.test(record)) {
                    return record;
                }
            }
        }
        throw new AssertionError("no matching dead-letter record on " + CHAT_DLT + " within " + TIMEOUT);
    }

    private List<ConsumerRecord<String, String>> pollDeadLetters(Duration window) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.nanoTime() + window.toNanos();
        while (System.nanoTime() < deadline) {
            deadLetterConsumer.poll(Duration.ofMillis(250)).forEach(records::add);
        }
        return records;
    }

    private void awaitProcessed(String eventId) {
        Awaitility.await().atMost(TIMEOUT).untilAsserted(() -> assertThat(processedCount(eventId))
                .isEqualTo(1));
    }

    private int processedCount(String eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from analytics_processed_events where source_event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static ChatMessageEvent chatEvent(String eventId) {
        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId(eventId);
        event.setStreamer("dlt-streamer");
        event.setUser("viewer");
        event.setMessage("hello");
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }
}
