package com.streamsense.analyticsservice.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.model.ChatLogLine;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatLogParserTest {

    private static final long START = 1_788_559_200_000L; // 2026-09-04T22:00:00Z
    private static final long DURATION = 7_200_000L;
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final ChatLogParser parser = new ChatLogParser(new ObjectMapper());

    @Test
    void readsAPlainJsonArrayWithOffsetsAndSortsIt() {
        List<ChatLogLine> lines = parser.parse(
                """
                [{"offsetSeconds": 3600, "user": "bob", "message": "!redbull"},
                 {"offset": "0:02:05", "username": "alice", "text": "gives you wings"},
                 {"offsetSeconds": 9999, "user": "late", "message": "after the end"}]
                """,
                START,
                DURATION,
                UTC);

        assertThat(lines)
                .containsExactly(
                        new ChatLogLine(125.0, "alice", "gives you wings"), new ChatLogLine(3600.0, "bob", "!redbull"));
    }

    @Test
    void readsTheDownloaderShapeWithTimestampsAndFragments() {
        List<ChatLogLine> lines = parser.parse(
                """
                {"comments": [
                  {"created_at": "2026-09-04T22:10:00Z", "commenter": {"display_name": "Alice"},
                   "message": {"fragments": [{"text": "hello "}, {"text": "there"}]}},
                  {"content_offset_seconds": 12.5, "commenter": {"login": "bob"}, "message": {"body": "hi"}},
                  {"created_at": "2026-09-04T21:00:00Z", "commenter": {"login": "early"}, "message": {"body": "before"}}
                ]}
                """,
                START,
                DURATION,
                UTC);

        assertThat(lines)
                .containsExactly(new ChatLogLine(12.5, "bob", "hi"), new ChatLogLine(600.0, "Alice", "hello there"));
    }

    @Test
    void readsACsvWithQuotedMessagesAndEitherAnOffsetOrATimestamp() {
        List<ChatLogLine> byOffset = parser.parse(
                """
                offset,user,message
                10,alice,"hello, world"
                20,bob,"she said ""hi""\"
                """,
                START,
                DURATION,
                UTC);
        assertThat(byOffset)
                .containsExactly(
                        new ChatLogLine(10.0, "alice", "hello, world"),
                        new ChatLogLine(20.0, "bob", "she said \"hi\""));

        List<ChatLogLine> byTimestamp = parser.parse(
                "timestamp\tusername\ttext\n1788559260000\talice\tone minute in\n2026-09-04T22:02:00Z\tbob\ttwo\n",
                START,
                DURATION,
                UTC);
        assertThat(byTimestamp)
                .containsExactly(new ChatLogLine(60.0, "alice", "one minute in"), new ChatLogLine(120.0, "bob", "two"));
    }

    @Test
    void readsAClientsTextLogAgainstTheRecordingStartInTheGivenZone() {
        // A Chatterino-style log kept in Berlin, where 22:00 UTC is 00:00 the next day.
        List<ChatLogLine> lines = parser.parse(
                """
                # Start logging at 2026-09-05 00:00:00 CEST
                [00:00:30] alice: first
                [00:59:59]  bob: almost an hour
                [01:00:01] carol: past the hour
                not a chat line
                """,
                START,
                DURATION,
                ZoneId.of("Europe/Berlin"));

        assertThat(lines)
                .containsExactly(
                        new ChatLogLine(30.0, "alice", "first"),
                        new ChatLogLine(3599.0, "bob", "almost an hour"),
                        new ChatLogLine(3601.0, "carol", "past the hour"));

        // Without a date the times are read as time into the recording.
        assertThat(parser.parse("[00:10:00] alice: ten minutes in", START, DURATION, UTC))
                .containsExactly(new ChatLogLine(600.0, "alice", "ten minutes in"));
    }

    @Test
    void refusesWhatItCannotRead() {
        assertThatThrownBy(() -> parser.parse("   ", START, DURATION, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        assertThatThrownBy(() -> parser.parse("just some prose\nwith no chat in it", START, DURATION, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no chat lines");
        assertThatThrownBy(() -> parser.parse("[{\"user\": \"a\", \"message\": \"no time\"}]", START, DURATION, UTC))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("offset,user,message\n99999,a,after the end\n", START, DURATION, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the recording");
        assertThatThrownBy(() -> parser.parse("{\"nothing\": 1}", START, DURATION, UTC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no array");
    }
}
