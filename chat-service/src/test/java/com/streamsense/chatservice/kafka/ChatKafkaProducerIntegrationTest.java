package com.streamsense.chatservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.chatservice.events.ChatMessageEvent;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
// Tests default to a SimpleMeterRegistry with exporters off; the Prometheus scrape below needs the real registry.
@AutoConfigureObservability
@EmbeddedKafka(
        partitions = 3,
        topics = {"stream.chat.messages"})
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "management.endpoints.web.exposure.include=prometheus",
            "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
            "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
            "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
            "streamsense.topics.chatMessages=stream.chat.messages",
            "streamsense.topics.sentimentEvents=stream.sentiment.events"
        })
class ChatKafkaProducerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.kafka.test.EmbeddedKafkaBroker embeddedKafkaBroker;

    private Consumer<String, ChatMessageEvent> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    private Consumer<String, ChatMessageEvent> createConsumerAtEnd(String group) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(group, "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatMessageEvent.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        Consumer<String, ChatMessageEvent> c = new DefaultKafkaConsumerFactory<>(
                        consumerProps, new StringDeserializer(), new JsonDeserializer<>(ChatMessageEvent.class, false))
                .createConsumer();
        c.subscribe(Collections.singletonList("stream.chat.messages"));
        // Trigger partition assignment. With auto.offset.reset=latest this positions
        // the consumer at the current end of the log so that messages produced by
        // other tests that ran earlier are not visible. Messages produced after
        // this poll() call will be picked up on the next poll().
        c.poll(Duration.ofSeconds(2));
        return c;
    }

    @Test
    void validIngest_producesRecordToKafka() throws Exception {
        consumer = createConsumerAtEnd("chat-service-test-group");

        String body =
                """
                                {
                                  "streamer": "test",
                                  "user": "u1",
                                  "message": "hello from test",
                                  "timestamp": 1710000000000
                                }
                                """;

        mockMvc.perform(post("/api/chat/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        ConsumerRecord<String, ChatMessageEvent> record =
                KafkaTestUtils.getSingleRecord(consumer, "stream.chat.messages", Duration.ofSeconds(10));

        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo("test");
        assertThat(record.value()).isNotNull();
        assertThat(record.value().getStreamer()).isEqualTo("test");
        assertThat(record.value().getUser()).isEqualTo("u1");
        assertThat(record.value().getMessage()).isEqualTo("hello from test");
        assertThat(record.value().getTimestamp()).isEqualTo(1710000000000L);
        assertThat(record.value().getEventId()).isNotBlank();
    }

    @Test
    void sameStreamerKey_alwaysRoutesToSamePartition_underThreePartitionTopology() throws Exception {
        consumer = createConsumerAtEnd("chat-partition-routing-test-group");

        String body1 =
                """
                                {
                                  "streamer": "routing-streamer",
                                  "user": "u1",
                                  "message": "first message",
                                  "timestamp": 1710000001000
                                }
                                """;
        String body2 =
                """
                                {
                                  "streamer": "routing-streamer",
                                  "user": "u2",
                                  "message": "second message",
                                  "timestamp": 1710000002000
                                }
                                """;

        mockMvc.perform(post("/api/chat/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1))
                .andExpect(status().isOk());

        ConsumerRecord<String, ChatMessageEvent> first =
                KafkaTestUtils.getSingleRecord(consumer, "stream.chat.messages", Duration.ofSeconds(10));

        assertThat(first.key()).isEqualTo("routing-streamer");
        assertThat(first.partition()).isBetween(0, 2);

        mockMvc.perform(post("/api/chat/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2))
                .andExpect(status().isOk());

        ConsumerRecord<String, ChatMessageEvent> second =
                KafkaTestUtils.getSingleRecord(consumer, "stream.chat.messages", Duration.ofSeconds(10));

        assertThat(second.key()).isEqualTo("routing-streamer");
        // Same key must route to same partition — this is the guarantee that enables
        // per-streamer ordering across the 3-partition topic.
        assertThat(second.partition()).isEqualTo(first.partition());
    }

    @Test
    void exposesKafkaProduceLatencyAsSecondsHistogram() throws Exception {
        consumer = createConsumerAtEnd("chat-metrics-test-group");

        mockMvc.perform(
                        post("/api/chat/ingest")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "streamer": "metrics-streamer",
                                                  "user": "u1",
                                                  "message": "measure me",
                                                  "timestamp": 1710000003000
                                                }
                                                """))
                .andExpect(status().isOk());
        KafkaTestUtils.getSingleRecord(consumer, "stream.chat.messages", Duration.ofSeconds(10));

        // Micrometer's Prometheus registry appends the base unit to timers, so the Grafana dashboards
        // must query the *_seconds series; the ack callback may land just after the consumer sees the record.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String scrape = mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(scrape).contains("streamsense_kafka_produce_latency_ms_seconds_bucket");
            double count = scrape.lines()
                    .filter(line -> line.startsWith("streamsense_kafka_produce_latency_ms_seconds_count"))
                    .mapToDouble(line -> Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("produce latency count series missing"));
            assertThat(count).isGreaterThanOrEqualTo(1.0d);
        });
    }
}
