package com.streamsense.chatservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.streamsense.chatservice.api.VodChatImportRequest;
import com.streamsense.chatservice.api.VodChatImportStatus;
import com.streamsense.chatservice.api.VodChatLine;
import com.streamsense.chatservice.events.ChatMessageEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VodChatImportServiceTest {

    private static final String VOD = "2750461300";
    private static final List<VodChatLine> LINES = List.of(
            new VodChatLine(12.5, "alice", "gives you wings"),
            new VodChatLine(3600.0, "bob", "!redbull"),
            new VodChatLine(3600.0, "bob", "!redbull"));

    private static VodChatImportRequest request(Long startOffset) {
        return new VodChatImportRequest("racer", VOD, 1_788_631_200_000L, "racer-vod-" + VOD, startOffset, LINES);
    }

    @Test
    void publishesEveryLineAtItsOriginalTimeWithAStableIdAndTheImportKey() {
        ChatEventIngestService ingest = mock(ChatEventIngestService.class);
        List<Runnable> tasks = new ArrayList<>();
        VodChatImportService service = new VodChatImportService(ingest, tasks::add);

        service.start(request(null));
        tasks.remove(0).run();

        ArgumentCaptor<ChatMessageEvent> events = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(ingest, times(3)).ingestTwitch(events.capture());
        ChatMessageEvent first = events.getAllValues().get(0);
        assertThat(first.getEventId()).startsWith("vod-" + VOD + "-log-").hasSize("vod-2750461300-log-".length() + 16);
        assertThat(first.getTimestamp()).isEqualTo(1_788_631_212_500L);
        assertThat(first.getSource()).isEqualTo("TWITCH_VOD_IMPORT");
        assertThat(first.getStreamSessionId()).isEqualTo("racer-vod-" + VOD);
        assertThat(first.getTwitchStreamId()).isEqualTo(VOD);
        assertThat(first.getUser()).isEqualTo("alice");
        // Two identical lines are two events, told apart by a counter; the same log again lands on the same ids.
        String second = events.getAllValues().get(1).getEventId();
        assertThat(events.getAllValues().get(2).getEventId()).isEqualTo(second + "-2");
        assertThat(VodChatImportService.lineId(LINES.get(0), new HashMap<>()))
                .isEqualTo(first.getEventId().substring("vod-2750461300-log-".length()));
        assertThat(service.status(VOD))
                .isPresent()
                .get()
                .extracting("state", "published", "total", "offsetSeconds", "stopRequested")
                .containsExactly("DONE", 3, 3, 3600.0, false);
    }

    @Test
    void aResumeSkipsTheLinesBeforeItsOffsetAndAStopLandsBetweenPages() {
        ChatEventIngestService ingest = mock(ChatEventIngestService.class);
        List<Runnable> tasks = new ArrayList<>();
        VodChatImportService service = new VodChatImportService(ingest, tasks::add);

        service.start(request(1000L));
        tasks.remove(0).run();
        verify(ingest, times(2)).ingestTwitch(any());
        assertThat(service.status(VOD).orElseThrow().offsetSeconds()).isEqualTo(3600.0);

        // A stop asked for while a page is being published takes effect before the next page.
        List<VodChatLine> many = new ArrayList<>();
        for (int i = 0; i < VodChatImportService.PAGE * 2; i++) {
            many.add(new VodChatLine((double) i, "user" + i, "line " + i));
        }
        ChatEventIngestService slow = mock(ChatEventIngestService.class);
        VodChatImportService stoppable = new VodChatImportService(slow, tasks::add);
        doAnswer(invocation -> {
                    stoppable.stop(VOD);
                    return null;
                })
                .when(slow)
                .ingestTwitch(any());
        stoppable.start(new VodChatImportRequest("racer", VOD, 0L, "racer-vod-" + VOD, null, many));
        tasks.remove(0).run();

        verify(slow, times(VodChatImportService.PAGE)).ingestTwitch(any());
        VodChatImportStatus stopped = stoppable.status(VOD).orElseThrow();
        assertThat(stopped.state()).isEqualTo("STOPPED");
        assertThat(stopped.stopRequested()).isTrue();
        assertThat(stopped.offsetSeconds()).isEqualTo(VodChatImportService.PAGE - 1);
        assertThat(stoppable.stop(VOD)).contains(stopped);
        assertThat(stoppable.stop("nobody")).isEmpty();
    }

    @Test
    void aQueuedImportStopsAtOnceAndPublishesNothing() {
        ChatEventIngestService ingest = mock(ChatEventIngestService.class);
        List<Runnable> tasks = new ArrayList<>();
        VodChatImportService service = new VodChatImportService(ingest, tasks::add);

        service.start(request(null));
        assertThat(service.stop(VOD)).isPresent().get().extracting("state").isEqualTo("STOPPED");
        tasks.remove(0).run();

        verify(ingest, never()).ingestTwitch(any());
        assertThat(service.status(VOD).orElseThrow().state()).isEqualTo("STOPPED");
    }
}
