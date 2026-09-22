package com.streamsense.videoservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** The current-deal lookup reads the deal and its logos, remembers "none" too, and turns a failure into an exception. */
class CurrentDealClientTest {

    private static final String DEAL_JSON =
            """
            {"id": 3, "streamer": "racer", "sponsor": "Red Bull", "startsAt": 1, "active": true,
             "logos": [{"id": 7, "contentType": "image/png", "width": 512, "height": 192, "ref": "s3://streamsense-logos/deals/3/a.png"}]}
            """;

    @Test
    void readsTheDealAndItsLogosAndKeepsTheAnswerForTheCacheLife() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(once(), requestTo("http://analytics:8085/api/analytics/streams/racer/current-deal"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(DEAL_JSON, MediaType.APPLICATION_JSON));
        SteppingClock clock = new SteppingClock(1_000_000L);
        CurrentDealClient client = new CurrentDealClient(template, "http://analytics:8085/", 60_000L, clock);

        Optional<CurrentDeal> first = client.find("@Racer");
        assertThat(first).isPresent();
        assertThat(first.get().sponsor()).isEqualTo("Red Bull");
        assertThat(first.get().logos())
                .containsExactly(new CurrentDeal.Logo(7, "s3://streamsense-logos/deals/3/a.png"));

        // Within the cache's life the same answer, without a request.
        clock.millis += 30_000L;
        assertThat(client.find("racer")).isEqualTo(first);
        server.verify();
    }

    @Test
    void noDealIsRememberedAsNoneAndAskedAgainOnceTheCacheExpires() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(once(), requestTo("http://analytics:8085/api/analytics/streams/nobody/current-deal"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(once(), requestTo("http://analytics:8085/api/analytics/streams/nobody/current-deal"))
                .andRespond(withSuccess(DEAL_JSON, MediaType.APPLICATION_JSON));
        SteppingClock clock = new SteppingClock(1_000_000L);
        CurrentDealClient client = new CurrentDealClient(template, "http://analytics:8085", 60_000L, clock);

        assertThat(client.find("nobody")).isEmpty();
        clock.millis += 59_000L;
        assertThat(client.find("nobody")).isEmpty();
        clock.millis += 2_000L;
        assertThat(client.find("nobody")).isPresent();
        server.verify();
    }

    @Test
    void aFailureIsAnExceptionForTheCallerToReportAsUnavailable() {
        RestTemplate template = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(template).build();
        server.expect(once(), requestTo("http://analytics:8085/api/analytics/streams/racer/current-deal"))
                .andRespond(withServerError());
        CurrentDealClient client =
                new CurrentDealClient(template, "http://analytics:8085", 60_000L, new SteppingClock(1_000_000L));

        assertThatThrownBy(() -> client.find("racer")).isInstanceOf(AnalyticsDependencyException.class);
    }

    private static final class SteppingClock extends Clock {
        long millis;

        SteppingClock(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
