package com.streamsense.analyticsservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.service.StreamSessionService;
import com.streamsense.analyticsservice.twitch.HelixStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** With the streamer role from the gateway, a request may name only its own channel. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.cloud.config.enabled=false",
            "eureka.client.enabled=false",
            "spring.kafka.listener.auto-startup=false",
            "spring.datasource.url=jdbc:h2:mem:analytics-scope-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.flyway.enabled=true",
            "streamsense.analytics.capture-session-close-check-ms=3600000"
        })
class ChannelScopeTest {

    private static final String LOGIN = "X-StreamSense-Auth-Login";
    private static final String ROLE = "X-StreamSense-Auth-Role";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StreamSessionService sessions;

    @Test
    void aStreamerReachesOnlyTheirOwnChannelByPathParameterBodyAndId() throws Exception {
        sessions.recordHelixLive(new HelixStream("9001", "owner", "Owner live", "Chatting", 5, 1_788_000_000_000L));
        sessions.recordHelixLive(
                new HelixStream("9002", "someone-else", "Other live", "Chatting", 5, 1_788_000_000_000L));
        long othersSession =
                sessions.list("someone-else", null, null, null).get(0).id();
        long ownSession = sessions.list("owner", null, null, null).get(0).id();

        // Path: the channel segment.
        mockMvc.perform(get("/api/analytics/streams/owner/sessions")
                        .header(LOGIN, "Owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/streams/someone-else/sessions")
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://streamsense.dev/problems/forbidden"))
                .andExpect(jsonPath("$.reason").value("channel_forbidden"));

        // Query parameter: the deals list must say whose.
        mockMvc.perform(get("/api/analytics/deals")
                        .param("streamer", "owner")
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/deals").header(LOGIN, "owner").header(ROLE, "streamer"))
                .andExpect(status().isForbidden());

        // Body: a deal can only be created for oneself, and the controller still receives the body.
        mockMvc.perform(post("/api/analytics/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamer\":\"someone-else\",\"sponsor\":\"Red Bull\",\"startsAt\":1788000000000}")
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isForbidden());
        String created = mockMvc.perform(post("/api/analytics/deals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streamer\":\"owner\",\"sponsor\":\"Red Bull\",\"startsAt\":1788000000000}")
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.streamer").value("owner"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String dealId = created.replaceAll(".*\"id\":(\\d+).*", "$1");

        // Id: the owner is looked up before the controller runs.
        mockMvc.perform(get("/api/analytics/deals/" + dealId)
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/deals/" + dealId)
                        .header(LOGIN, "someone-else")
                        .header(ROLE, "streamer"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analytics/sessions/" + ownSession)
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/sessions/" + othersSession)
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isForbidden());
        // A session that does not exist is still a 404, not a 403: nothing to hide.
        mockMvc.perform(get("/api/analytics/sessions/999999")
                        .header(LOGIN, "owner")
                        .header(ROLE, "streamer"))
                .andExpect(status().isNotFound());

        // Operators and unscoped callers (the gateway's own resolvers) see everything.
        mockMvc.perform(get("/api/analytics/streams/someone-else/sessions")
                        .header(LOGIN, "ops")
                        .header(ROLE, "operator"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/analytics/sessions/" + othersSession)).andExpect(status().isOk());
    }
}
