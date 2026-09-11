package com.streamsense.analyticsservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.service.StreamSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** A deal's totals cover every one of its sessions, not the first page of them. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-deal-totals-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.capture-session-close-check-ms=3600000"
        })
class DealTotalsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StreamSessionService sessions;

    @Test
    void aDealWithMoreSessionsThanAListPageStillCountsThemAll() throws Exception {
        String streamer = "marathon";
        long start = 1_780_000_000_000L;
        String created = mockMvc.perform(post("/api/analytics/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamer\":\"" + streamer + "\",\"sponsor\":\"Red Bull\",\"startsAt\":" + start
                                + "}"))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String dealId = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        // 205 recordings inside the deal, one an hour: past the old cap of 200 on a list page.
        for (int i = 0; i < 205; i++) {
            sessions.recordVod(
                    streamer,
                    "vod-" + i,
                    null,
                    "Stream " + i,
                    start + i * 3_600_000L,
                    600_000L,
                    streamer + "-vod-" + i,
                    100);
        }

        mockMvc.perform(get("/api/analytics/deals/" + dealId + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.streams").value(205))
                .andExpect(jsonPath("$.sessions.length()").value(205))
                .andExpect(jsonPath("$.totals.streamedMs").value(205 * 600_000L));
    }
}
