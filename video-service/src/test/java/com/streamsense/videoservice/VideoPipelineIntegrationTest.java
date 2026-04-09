package com.streamsense.videoservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import com.streamsense.videoservice.events.SponsorDetectionEvent;
import com.streamsense.videoservice.persistence.SponsorDetectionEntity;
import com.streamsense.videoservice.persistence.SponsorDetectionRepository;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = { "stream.video.frames", "stream.sponsor.detections" })
@TestPropertySource(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:video-pipeline;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.group-id=video-service-test-group",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        "spring.kafka.consumer.properties.spring.json.value.default.type=com.streamsense.videoservice.events.FrameData",
        "spring.kafka.consumer.properties.spring.json.use.type.headers=false",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer",
        "streamsense.topics.videoFrames=stream.video.frames",
        "streamsense.topics.sponsorDetections=stream.sponsor.detections",
        "streamsense.ml.base-url=http://ml-engine:8000",
        "streamsense.history.defaultLimit=20",
        "streamsense.history.maxLimit=100",
        "streamsense.payload.maxFrameRefLength=512",
        "resilience4j.retry.instances.mlSponsor.maxAttempts=3",
        "resilience4j.retry.instances.mlSponsor.waitDuration=10ms",
        "resilience4j.retry.instances.mlSponsor.ignoreExceptions[0]=java.lang.IllegalStateException",
        "resilience4j.retry.instances.mlSponsor.ignoreExceptions[1]=java.lang.IllegalArgumentException",
        "resilience4j.circuitbreaker.instances.mlSponsor.minimumNumberOfCalls=10",
        "resilience4j.circuitbreaker.instances.mlSponsor.slidingWindowSize=10",
        "resilience4j.circuitbreaker.instances.mlSponsor.waitDurationInOpenState=1s",
        "resilience4j.bulkhead.instances.mlSponsor.maxConcurrentCalls=2"
})
class VideoPipelineIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private SponsorDetectionRepository repository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private MockMvc mockMvc;

    private MockRestServiceServer mockServer;
    private Consumer<String, SponsorDetectionEvent> sponsorConsumer;

    @BeforeEach
    void setUp() {
        for (MessageListenerContainer container : kafkaListenerEndpointRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
        }

        repository.deleteAll();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("sponsor-events-test-group", "true",
                embeddedKafkaBroker);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SponsorDetectionEvent.class.getName());
        consumerProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        sponsorConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(SponsorDetectionEvent.class, false))
                .createConsumer();
        sponsorConsumer.subscribe(Collections.singletonList("stream.sponsor.detections"));
    }

    @AfterEach
    void tearDown() {
        if (sponsorConsumer != null) {
            sponsorConsumer.close();
        }
    }

    @Test
    void uploadedFrame_isScoredPersistedAndPublished() throws Exception {
        mockServer.expect(requestTo("http://ml-engine:8000/ml/sponsor"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{" +
                                "\"sponsor\":\"Nike\"," +
                                "\"confidence\":0.91," +
                                "\"modelVersion\":\"stub-v1\"," +
                                "\"x\":0.12," +
                                "\"y\":0.18," +
                                "\"width\":0.31," +
                                "\"height\":0.24}",
                        MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/video/upload-frame")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "streamer": "test-streamer",
                          "frameRef": "frames/test-001.png",
                          "frameSequence": 1,
                          "capturedAt": 1710000000000
                        }
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(repository.findAll()).hasSize(1));

        SponsorDetectionEntity persisted = repository.findAll().getFirst();
        assertThat(persisted.getStreamer()).isEqualTo("test-streamer");
        assertThat(persisted.getSponsor()).isEqualTo("Nike");
        assertThat(persisted.getModelVersion()).isEqualTo("stub-v1");

        ConsumerRecord<String, SponsorDetectionEvent> record = KafkaTestUtils.getSingleRecord(
                sponsorConsumer,
                "stream.sponsor.detections",
                Duration.ofSeconds(10));

        assertThat(record.value().getStreamer()).isEqualTo("test-streamer");
        assertThat(record.value().getSponsor()).isEqualTo("Nike");
        assertThat(record.value().getConfidence()).isEqualTo(0.91d);

        mockServer.verify();
    }

    @Test
    void sponsorMlFailure_persistsAndPublishesFallbackDetection() throws Exception {
        mockServer.expect(ExpectedCount.times(3), requestTo("http://ml-engine:8000/ml/sponsor"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        mockMvc.perform(post("/api/video/upload-frame")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "streamer": "fallback-streamer",
                          "frameRef": "frames/fallback.png",
                          "frameSequence": 2,
                          "capturedAt": 1710000001000
                        }
                        """))
                .andExpect(status().isAccepted());

        Awaitility.await()
                .atMost(Duration.ofSeconds(15))
                .untilAsserted(() -> assertThat(repository.findAll())
                        .extracting(SponsorDetectionEntity::getStreamer, SponsorDetectionEntity::getSponsor,
                                SponsorDetectionEntity::getModelVersion)
                        .containsExactly(org.assertj.core.api.Assertions.tuple("fallback-streamer", "UNKNOWN", "fallback")));

        ConsumerRecord<String, SponsorDetectionEvent> record = KafkaTestUtils.getSingleRecord(
                sponsorConsumer,
                "stream.sponsor.detections",
                Duration.ofSeconds(10));

        assertThat(record.value().getSponsor()).isEqualTo("UNKNOWN");
        assertThat(record.value().getConfidence()).isEqualTo(0.0d);
        assertThat(record.value().getModelVersion()).isEqualTo("fallback");

        mockServer.verify();
    }

    @Test
    void recentEndpoint_returnsPersistedResultsInDescendingRecencyOrder() throws Exception {
        repository.save(entity("det-1", "frame-1", "test-streamer", "frames/a.png", 1, 1710000000000L,
                1710000000100L, "Nike", 0.81d, "stub-v1", 0.1d, 0.2d, 0.3d, 0.4d));
        repository.save(entity("det-2", "frame-2", "test-streamer", "frames/b.png", 2, 1710000001000L,
                1710000001100L, "Prime", 0.74d, "stub-v1", 0.2d, 0.1d, 0.25d, 0.22d));

        mockMvc.perform(get("/api/video/detections/recent")
                .param("streamer", "test-streamer")
                .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].detectionEventId").value("det-2"))
                .andExpect(jsonPath("$[0].sponsor").value("Prime"))
                .andExpect(jsonPath("$[1].detectionEventId").value("det-1"))
                .andExpect(jsonPath("$[1].sponsor").value("Nike"));
    }

    private SponsorDetectionEntity entity(
            String detectionEventId,
            String sourceFrameId,
            String streamer,
            String frameRef,
            long frameSequence,
            long capturedAt,
            long processedAt,
            String sponsor,
            double confidence,
            String modelVersion,
            double x,
            double y,
            double width,
            double height) {
        SponsorDetectionEntity entity = new SponsorDetectionEntity();
        entity.setDetectionEventId(detectionEventId);
        entity.setSourceFrameId(sourceFrameId);
        entity.setStreamer(streamer);
        entity.setFrameRef(frameRef);
        entity.setFrameSequence(frameSequence);
        entity.setCapturedAt(capturedAt);
        entity.setProcessedAt(processedAt);
        entity.setSponsor(sponsor);
        entity.setConfidence(confidence);
        entity.setModelVersion(modelVersion);
        entity.setX(x);
        entity.setY(y);
        entity.setWidth(width);
        entity.setHeight(height);
        return entity;
    }
}
