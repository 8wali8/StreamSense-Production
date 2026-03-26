package com.streamsense.chatservice.kafka;

import com.streamsense.chatservice.events.ChatMessageEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = { "stream.chat.messages" })
@TestPropertySource(properties = {
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
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

        @Test
        void validIngest_producesRecordToKafka() throws Exception {
                Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("chat-service-test-group", "true",
                                embeddedKafkaBroker);

                consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
                consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatMessageEvent.class.getName());
                consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

                consumer = new DefaultKafkaConsumerFactory<>(
                                consumerProps,
                                new StringDeserializer(),
                                new JsonDeserializer<>(ChatMessageEvent.class, false)).createConsumer();

                consumer.subscribe(Collections.singletonList("stream.chat.messages"));

                String body = """
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

                ConsumerRecord<String, ChatMessageEvent> record = KafkaTestUtils.getSingleRecord(consumer,
                                "stream.chat.messages", Duration.ofSeconds(10));

                assertThat(record).isNotNull();
                assertThat(record.key()).isEqualTo("test");
                assertThat(record.value()).isNotNull();
                assertThat(record.value().getStreamer()).isEqualTo("test");
                assertThat(record.value().getUser()).isEqualTo("u1");
                assertThat(record.value().getMessage()).isEqualTo("hello from test");
                assertThat(record.value().getTimestamp()).isEqualTo(1710000000000L);
                assertThat(record.value().getEventId()).isNotBlank();
        }
}