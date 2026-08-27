package com.streamsense.chatservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.chatservice.config.StreamSenseProperties;

class TwitchVodCommentClientTest {

    private static final String VOD_ID = "2750461300";

    @Test
    void cachesDownloadsPerVodAndStartOffset() {
        TwitchVodCommentClient client = spy(new TwitchVodCommentClient(new StreamSenseProperties(), new ObjectMapper()));
        List<TwitchVodChatComment> fromFifty = List.of(new TwitchVodChatComment("c-50", "viewer", "early", 50.0));
        List<TwitchVodChatComment> fromHundred = List.of(new TwitchVodChatComment("c-100", "viewer", "late", 100.0));
        doReturn(fromFifty).when(client).downloadComments(VOD_ID, 50.0);
        doReturn(fromHundred).when(client).downloadComments(VOD_ID, 100.0);

        // Two aliases on the same VOD with different offsets must not share a download.
        assertThat(client.fetchComments(VOD_ID, 50.0)).isEqualTo(fromFifty);
        assertThat(client.fetchComments(VOD_ID, 100.0)).isEqualTo(fromHundred);
        assertThat(client.fetchComments(VOD_ID, 50.0)).isEqualTo(fromFifty);

        verify(client, times(1)).downloadComments(VOD_ID, 50.0);
        verify(client, times(1)).downloadComments(VOD_ID, 100.0);
    }

    @Test
    void keysTheCacheOnWholeSecondsSoTheKeyMatchesWhatTwitchIsAsked() {
        TwitchVodCommentClient client = spy(new TwitchVodCommentClient(new StreamSenseProperties(), new ObjectMapper()));
        List<TwitchVodChatComment> comments = List.of(new TwitchVodChatComment("c-30", "viewer", "text", 30.0));
        doReturn(comments).when(client).downloadComments(VOD_ID, 30.2);

        assertThat(client.fetchComments(VOD_ID, 30.2)).isEqualTo(comments);
        // 30.9 floors to the same contentOffsetSeconds as 30.2, so it is served from the cache.
        assertThat(client.fetchComments(VOD_ID, 30.9)).isEqualTo(comments);

        verify(client, times(1)).downloadComments(VOD_ID, 30.2);
    }
}
