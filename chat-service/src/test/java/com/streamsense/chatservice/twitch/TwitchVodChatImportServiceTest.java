package com.streamsense.chatservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.streamsense.chatservice.api.VodChatImportRequest;
import com.streamsense.chatservice.api.VodChatImportStatus;
import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.service.ChatEventIngestService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TwitchVodChatImportServiceTest {

    private static final String VOD = "2750461300";

    @Test
    void publishesEveryCommentAtItsOriginalTimeWithTheImportKeyPageByPage() {
        TwitchVodCommentClient comments = mock(TwitchVodCommentClient.class);
        ChatEventIngestService ingest = mock(ChatEventIngestService.class);
        // Two pages, handed over one at a time the way the client streams them from Twitch.
        doAnswer(invocation -> {
                    Consumer<List<TwitchVodChatComment>> consumer = invocation.getArgument(2);
                    consumer.accept(List.of(new TwitchVodChatComment("c1", "alice", "gives you wings", 12.5)));
                    consumer.accept(List.of(new TwitchVodChatComment("c2", "bob", "!redbull", 3600.0)));
                    return null;
                })
                .when(comments)
                .forEachPage(eq(VOD), anyDouble(), any(), any());
        TwitchVodChatImportService service = new TwitchVodChatImportService(comments, ingest);

        service.run("racer", VOD, 1_788_631_200_000L, "racer-vod-" + VOD, 0);

        ArgumentCaptor<ChatMessageEvent> events = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(ingest, org.mockito.Mockito.times(2)).ingestTwitch(events.capture());
        ChatMessageEvent first = events.getAllValues().get(0);
        assertThat(first.getEventId()).isEqualTo("vod-" + VOD + "-import-c1");
        assertThat(first.getTimestamp()).isEqualTo(1_788_631_212_500L);
        assertThat(first.getSource()).isEqualTo("TWITCH_VOD_IMPORT");
        assertThat(first.getStreamSessionId()).isEqualTo("racer-vod-" + VOD);
        assertThat(first.getTwitchStreamId()).isEqualTo(VOD);
        assertThat(events.getAllValues().get(1).getTimestamp()).isEqualTo(1_788_634_800_000L);
        assertThat(service.status(VOD))
                .isPresent()
                .get()
                .extracting("state", "published", "total", "offsetSeconds", "stopRequested")
                .containsExactly("DONE", 2, 2, 3600.0, false);
    }

    @Test
    void aStopLandsBetweenPagesAndTheOffsetReachedIsWhereAResumePagesFrom() {
        TwitchVodCommentClient comments = mock(TwitchVodCommentClient.class);
        ChatEventIngestService ingest = mock(ChatEventIngestService.class);
        List<Runnable> tasks = new ArrayList<>();
        TwitchVodChatImportService service = new TwitchVodChatImportService(comments, ingest, tasks::add);
        // The client honours the stop between pages exactly as the real one does.
        doAnswer(invocation -> {
                    Consumer<List<TwitchVodChatComment>> consumer = invocation.getArgument(2);
                    BooleanSupplier stop = invocation.getArgument(3);
                    consumer.accept(List.of(new TwitchVodChatComment("c1", "alice", "hi", 40.0)));
                    service.stop(VOD);
                    if (!stop.getAsBoolean()) {
                        consumer.accept(List.of(new TwitchVodChatComment("c2", "bob", "late", 900.0)));
                    }
                    return null;
                })
                .when(comments)
                .forEachPage(eq(VOD), anyDouble(), any(), any());

        // The import task runs on this thread, so the assertions see its end state.
        service.start(new VodChatImportRequest("racer", VOD, 1_788_631_200_000L, "racer-vod-" + VOD, null));
        tasks.remove(0).run();

        verify(ingest, org.mockito.Mockito.times(1)).ingestTwitch(any());
        VodChatImportStatus stopped = service.status(VOD).orElseThrow();
        assertThat(stopped.state()).isEqualTo("STOPPED");
        assertThat(stopped.stopRequested()).isTrue();
        assertThat(stopped.offsetSeconds()).isEqualTo(40.0);
        // Stopping again changes nothing; stopping an unknown recording answers empty.
        assertThat(service.stop(VOD)).contains(stopped);
        assertThat(service.stop("nobody")).isEmpty();

        // A resume pages from the offset reached rather than from the start.
        service.start(new VodChatImportRequest("racer", VOD, 1_788_631_200_000L, "racer-vod-" + VOD, 40L));
        tasks.remove(0).run();
        verify(comments).forEachPage(eq(VOD), eq(40.0), any(), any());
    }

    @Test
    void aQueuedImportStopsAtOnceAndNeverPagesTwitch() {
        TwitchVodCommentClient comments = mock(TwitchVodCommentClient.class);
        ChatEventIngestService ingest = mock(ChatEventIngestService.class);
        List<Runnable> tasks = new ArrayList<>();
        TwitchVodChatImportService service = new TwitchVodChatImportService(comments, ingest, tasks::add);

        service.start(new VodChatImportRequest("racer", VOD, 1_788_631_200_000L, "racer-vod-" + VOD, null));
        assertThat(service.stop(VOD)).isPresent().get().extracting("state").isEqualTo("STOPPED");
        tasks.remove(0).run();

        verify(comments, never()).forEachPage(any(), anyDouble(), any(), any());
        assertThat(service.status(VOD).orElseThrow().state()).isEqualTo("STOPPED");
    }
}
