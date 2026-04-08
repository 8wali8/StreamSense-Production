package com.streamsense.sentimentservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import com.streamsense.sentimentservice.events.ChatMessageEvent;
import com.streamsense.sentimentservice.events.SentimentAnalysisEvent;
import com.streamsense.sentimentservice.persistence.SentimentRecordEntity;
import com.streamsense.sentimentservice.persistence.SentimentRecordRepository;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = { "stream.chat.messages", "stream.sentiment.events", "stream.chat.messages.dlt" })
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:sentiment-pipeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=sentiment-service-test-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.streamsense.sentimentservice.events.ChatMessageEvent",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "streamsense.topics.chatMessages=stream.chat.messages",
        "streamsense.topics.sentimentEvents=stream.sentiment.events",
        "streamsense.topics.chatMessagesDlt=stream.chat.messages.dlt",
        "streamsense.ml.base-url=http://ml-engine:8000",
        "streamsense.history.default-limit=20",
        "streamsense.history.max-limit=100",
        "streamsense.processing.retryBackoffMs=50",
        "streamsense.processing.maxRetries=2",
        "resilience4j.retry.instances.mlSentiment.maxAttempts=3",
        "resilience4j.retry.instances.mlSentiment.waitDuration=10ms",
        "resilience4j.retry.instances.mlSentiment.ignoreExceptions[0]=java.lang.IllegalStateException",
        "resilience4j.retry.instances.mlSentiment.ignoreExceptions[1]=java.lang.IllegalArgumentException",
        "resilience4j.circuitbreaker.instances.mlSentiment.minimumNumberOfCalls=10",
        "resilience4j.circuitbreaker.instances.mlSentiment.slidingWindowSize=10",
        "resilience4j.circuitbreaker.instances.mlSentiment.waitDurationInOpenState=1s",
        "resilience4j.bulkhead.instances.mlSentiment.maxConcurrentCalls=2"
})
class SentimentPipelineIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private SentimentRecordRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private MockRestServiceServer mockServer;
    private Consumer<String, SentimentAnalysisEvent> sentimentConsumer;
    private Consumer<String, ChatMessageEvent> deadLetterConsumer;

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
        }

        repository.deleteAll();
        circuitBreakerRegistry.circuitBreaker("mlSentiment").reset();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("sentiment-events-test-group", "true",
                embeddedKafkaBroker);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SentimentAnalysisEvent.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        sentimentConsumer = new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(SentimentAnalysisEvent.class, false))
                .createConsumer();
        sentimentConsumer.subscribe(Collections.singletonList("stream.sentiment.events"));

        Map<String, Object> deadLetterConsumerProps = KafkaTestUtils.consumerProps("sentiment-dlt-test-group", "true",
                embeddedKafkaBroker);
        deadLetterConsumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        deadLetterConsumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ChatMessageEvent.class.getName());
        deadLetterConsumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        deadLetterConsumer = new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(
                deadLetterConsumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(ChatMessageEvent.class, false))
                .createConsumer();
        deadLetterConsumer.subscribe(Collections.singletonList("stream.chat.messages.dlt"));
    }

    @AfterEach
    void tearDown() {
        if (sentimentConsumer != null) {
            sentimentConsumer.close();
        }
        if (deadLetterConsumer != null) {
            deadLetterConsumer.close();
        }
    }

    @Test
    void consumedChatEvent_isScoredPersistedAndPublished() throws Exception {
        mockServer.expect(requestTo("http://ml-engine:8000/ml/sentiment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"label\":\"POSITIVE\",\"score\":0.87,\"modelVersion\":\"stub-v1\"}",
                        MediaType.APPLICATION_JSON));

        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("evt-123");
        event.setStreamer("test-streamer");
        event.setUser("u1");
        event.setMessage("great stream");
        event.setTimestamp(1710000000000L);

        testChatKafkaTemplate().send("stream.chat.messages", "test-streamer", event).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(repository.findAll()).hasSize(1));

        SentimentRecordEntity persisted = repository.findAll().getFirst();
        assertThat(persisted.getSourceEventId()).isEqualTo("evt-123");
        assertThat(persisted.getStreamer()).isEqualTo("test-streamer");
        assertThat(persisted.getUserName()).isEqualTo("u1");
        assertThat(persisted.getLabel()).isEqualTo("POSITIVE");
        assertThat(persisted.getScore()).isEqualTo(0.87d);
        assertThat(persisted.getModelVersion()).isEqualTo("stub-v1");

        ConsumerRecord<String, SentimentAnalysisEvent> record = KafkaTestUtils.getSingleRecord(
                sentimentConsumer,
                "stream.sentiment.events",
                Duration.ofSeconds(10));

        assertThat(record.value().getSourceEventId()).isEqualTo("evt-123");
        assertThat(record.value().getStreamer()).isEqualTo("test-streamer");
        assertThat(record.value().getLabel()).isEqualTo("POSITIVE");
        assertThat(record.value().getChatTimestamp()).isEqualTo(1710000000000L);

        mockServer.verify();
    }

    @Test
    void mlDependencyFailure_persistsAndPublishesFallbackSentiment() throws Exception {
        mockServer.expect(ExpectedCount.times(3), requestTo("http://ml-engine:8000/ml/sentiment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("evt-fallback");
        event.setStreamer("fallback-streamer");
        event.setUser("u-fallback");
        event.setMessage("ml is down");
        event.setTimestamp(1710000002000L);

        testChatKafkaTemplate().send("stream.chat.messages", "fallback-streamer", event).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(repository.findAll())
                        .extracting(SentimentRecordEntity::getSourceEventId, SentimentRecordEntity::getLabel,
                                SentimentRecordEntity::getModelVersion)
                        .contains(tuple("evt-fallback", "NEUTRAL", "fallback")));

        ConsumerRecord<String, SentimentAnalysisEvent> record = KafkaTestUtils.getSingleRecord(
                sentimentConsumer,
                "stream.sentiment.events",
                Duration.ofSeconds(10));

        assertThat(record.value().getSourceEventId()).isEqualTo("evt-fallback");
        assertThat(record.value().getLabel()).isEqualTo("NEUTRAL");
        assertThat(record.value().getScore()).isEqualTo(0.0d);
        assertThat(record.value().getModelVersion()).isEqualTo("fallback");

        mockServer.verify();
    }

    @Test
    void transientMlFailure_retriesAndEventuallySucceeds() throws Exception {
        mockServer.expect(ExpectedCount.times(2), requestTo("http://ml-engine:8000/ml/sentiment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        mockServer.expect(ExpectedCount.once(), requestTo("http://ml-engine:8000/ml/sentiment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"label\":\"POSITIVE\",\"score\":0.65,\"modelVersion\":\"stub-v1\"}",
                        MediaType.APPLICATION_JSON));

        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("evt-retry");
        event.setStreamer("retry-streamer");
        event.setUser("u-retry");
        event.setMessage("transient issue");
        event.setTimestamp(1710000003000L);

        testChatKafkaTemplate().send("stream.chat.messages", "retry-streamer", event).get();

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(repository.findAll())
                        .extracting(SentimentRecordEntity::getSourceEventId, SentimentRecordEntity::getLabel,
                                SentimentRecordEntity::getModelVersion)
                        .contains(tuple("evt-retry", "POSITIVE", "stub-v1")));

        mockServer.verify();
    }

    @Test
    void invalidMlResponse_isDeadLetteredInsteadOfSilentlyDropped() throws Exception {
        mockServer.expect(requestTo("http://ml-engine:8000/ml/sentiment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"label\":\"\",\"score\":0.12,\"modelVersion\":\"stub-v1\"}",
                        MediaType.APPLICATION_JSON));

        ChatMessageEvent event = new ChatMessageEvent();
        event.setEventId("evt-dlt");
        event.setStreamer("dlt-streamer");
        event.setUser("u-dlt");
        event.setMessage("bad response path");
        event.setTimestamp(1710000004000L);

        testChatKafkaTemplate().send("stream.chat.messages", "dlt-streamer", event).get();

        ConsumerRecord<String, ChatMessageEvent> dltRecord = KafkaTestUtils.getSingleRecord(
                deadLetterConsumer,
                "stream.chat.messages.dlt",
                Duration.ofSeconds(10));

        assertThat(dltRecord.value().getEventId()).isEqualTo("evt-dlt");
        assertThat(dltRecord.value().getStreamer()).isEqualTo("dlt-streamer");

        Awaitility.await()
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(repository.findAll())
                        .noneMatch(record -> "evt-dlt".equals(record.getSourceEventId())));

        mockServer.verify();
    }

    @Test
    void recentEndpoint_returnsPersistedResultsInDescendingRecencyOrder() throws Exception {
        repository.save(entity("sent-1", "src-1", "test-streamer", "u1", "first", 1710000000000L, 1710000000100L,
                "NEGATIVE", -0.7d, "stub-v1"));
        repository.save(entity("sent-2", "src-2", "test-streamer", "u2", "second", 1710000001000L,
                1710000001100L, "POSITIVE", 0.8d, "stub-v1"));

        mockMvc.perform(get("/api/sentiment/recent")
                .param("streamer", "test-streamer")
                .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sentimentEventId").value("sent-2"))
                .andExpect(jsonPath("$[0].label").value("POSITIVE"))
                .andExpect(jsonPath("$[1].sentimentEventId").value("sent-1"))
                .andExpect(jsonPath("$[1].label").value("NEGATIVE"));
    }

    private KafkaTemplate<String, ChatMessageEvent> testChatKafkaTemplate() {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    private SentimentRecordEntity entity(
            String sentimentEventId,
            String sourceEventId,
            String streamer,
            String user,
            String message,
            long chatTimestamp,
            long processedAt,
            String label,
            double score,
            String modelVersion) {
        SentimentRecordEntity entity = new SentimentRecordEntity();
        entity.setSentimentEventId(sentimentEventId);
        entity.setSourceEventId(sourceEventId);
        entity.setStreamer(streamer);
        entity.setUserName(user);
        entity.setMessage(message);
        entity.setChatTimestamp(chatTimestamp);
        entity.setProcessedAt(processedAt);
        entity.setLabel(label);
        entity.setScore(score);
        entity.setModelVersion(modelVersion);
        return entity;
    }
}
