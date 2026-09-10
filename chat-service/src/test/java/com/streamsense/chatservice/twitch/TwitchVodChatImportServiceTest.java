package com.streamsense.chatservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.streamsense.chatservice.events.ChatMessageEvent;
import com.streamsense.chatservice.service.ChatEventIngestService;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TwitchVodChatImportServiceTest {

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
                .forEachPage(eq("2750461300"), anyDouble(), any());
        TwitchVodChatImportService service = new TwitchVodChatImportService(comments, ingest);

        service.run("racer", "2750461300", 1_788_631_200_000L, "racer-vod-2750461300");

        ArgumentCaptor<ChatMessageEvent> events = ArgumentCaptor.forClass(ChatMessageEvent.class);
        verify(ingest, org.mockito.Mockito.times(2)).ingestTwitch(events.capture());
        ChatMessageEvent first = events.getAllValues().get(0);
        assertThat(first.getEventId()).isEqualTo("vod-2750461300-import-c1");
        assertThat(first.getTimestamp()).isEqualTo(1_788_631_212_500L);
        assertThat(first.getSource()).isEqualTo("TWITCH_VOD_IMPORT");
        assertThat(first.getStreamSessionId()).isEqualTo("racer-vod-2750461300");
        assertThat(first.getTwitchStreamId()).isEqualTo("2750461300");
        assertThat(events.getAllValues().get(1).getTimestamp()).isEqualTo(1_788_634_800_000L);
        assertThat(service.status("2750461300"))
                .isPresent()
                .get()
                .extracting("state", "published", "total")
                .containsExactly("DONE", 2, 2);
    }
}
