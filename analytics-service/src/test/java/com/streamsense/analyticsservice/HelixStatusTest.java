package com.streamsense.analyticsservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.streamsense.analyticsservice.api.HelixPollStatus;
import com.streamsense.analyticsservice.controller.HelixStatusController;
import com.streamsense.analyticsservice.twitch.StreamSessionPoller;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** The Helix status route: disabled in this context (no credentials), and the poller's snapshot when there is one. */
@SpringBootTest
@AutoConfigureMockMvc
class HelixStatusTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsDisabledWhenThePollerIsOff() throws Exception {
        mockMvc.perform(get("/api/analytics/helix/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.lastPollAt").isEmpty());
    }

    @Test
    void reportsThePollerSnapshotWhenItRuns() {
        StreamSessionPoller poller = mock(StreamSessionPoller.class);
        when(poller.status())
                .thenReturn(HelixPollStatus.idle(60_000L).polled(1_799_999_990_000L, 1_800_000_000_000L, 3, 1, 0));
        @SuppressWarnings("unchecked")
        ObjectProvider<StreamSessionPoller> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(poller);

        HelixPollStatus status = new HelixStatusController(provider).status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.watched()).isEqualTo(3);
        assertThat(status.live()).isEqualTo(1);
        assertThat(status.lastPollAt()).isEqualTo(1_800_000_000_000L);
    }
}
