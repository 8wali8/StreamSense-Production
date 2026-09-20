package com.streamsense.analyticsservice.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsense.analyticsservice.model.ChatLogLine;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads a chat log a streamer supplies for a recording into lines of (seconds into the recording, user,
 * message). Three shapes are understood, told apart by looking at the content, not the file name:
 *
 * <ul>
 *   <li>JSON: an array, or an object holding one under {@code comments} or {@code messages}. Each item
 *       names the time as an offset in seconds ({@code offsetSeconds}, {@code offset},
 *       {@code content_offset_seconds}, {@code seconds}) or a timestamp ({@code timestamp},
 *       {@code created_at}, {@code time}: ISO-8601 or epoch), the user ({@code user}, {@code username},
 *       {@code login}, {@code name}, {@code display_name}, or a {@code commenter} object with one of
 *       those), and the message ({@code message}, {@code text}, {@code body}, or a {@code message}
 *       object with {@code body} or {@code text}).
 *   <li>CSV or TSV with a header row naming those same columns.
 *   <li>A chat client's text log, one line per message as {@code [HH:MM:SS] user: message}, with an
 *       optional {@code # Start logging at YYYY-MM-DD ...} header naming the day. The times are wall
 *       clock in the zone the caller names (the streamer's browser, by default), so they are placed
 *       against the recording's start.
 * </ul>
 *
 * Lines before the recording starts or after it ends are dropped; the rest are sorted by offset.
 */
@Component
public class ChatLogParser {

    /** The most lines one log may hold; a twelve-hour stream of a large channel stays well under it. */
    public static final int MAX_LINES = 500_000;

    private static final List<String> OFFSET_KEYS = List.of(
            "offsetSeconds", "offset_seconds", "offset", "content_offset_seconds", "seconds", "time_in_seconds");
    private static final List<String> TIMESTAMP_KEYS = List.of("timestamp", "created_at", "createdAt", "time", "date");
    private static final List<String> USER_KEYS =
            List.of("user", "username", "login", "commenter", "display_name", "displayName", "name", "author");
    private static final List<String> MESSAGE_KEYS = List.of("message", "text", "body", "content");
    private static final Pattern TEXT_LINE = Pattern.compile("^\\[?(\\d{1,2}:\\d{2}:\\d{2})]?\\s+([^:\\s]+):\\s?(.*)$");
    private static final Pattern TEXT_HEADER_DATE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern HMS = Pattern.compile("^(\\d{1,3}):(\\d{2}):(\\d{2})(?:\\.\\d+)?$");

    private final ObjectMapper objectMapper;

    public ChatLogParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param content the file's text
     * @param recordingStartMs when the recording began, for logs that carry timestamps rather than offsets
     * @param recordingDurationMs how long it runs, so lines past its end are dropped
     * @param zone the zone a text log's wall-clock times are in
     */
    public List<ChatLogLine> parse(String content, long recordingStartMs, long recordingDurationMs, ZoneId zone) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("the chat log is empty");
        }
        List<ChatLogLine> lines;
        String firstLine = text.lines().findFirst().orElse("").strip();
        if (firstLine.startsWith("#") || TEXT_LINE.matcher(firstLine).matches()) {
            // A chat client's log also starts with "[", so its line shape is checked before JSON is tried.
            lines = parseText(text, recordingStartMs, zone);
        } else if (text.startsWith("[") || text.startsWith("{")) {
            lines = parseJson(text, recordingStartMs);
        } else if (looksLikeTable(text)) {
            lines = parseTable(text, recordingStartMs);
        } else {
            lines = parseText(text, recordingStartMs, zone);
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(
                    "no chat lines could be read from the log; expected JSON, CSV with a header, or lines like"
                            + " [12:34:56] user: message");
        }
        double end = recordingDurationMs / 1000.0 + 60;
        List<ChatLogLine> kept = new ArrayList<>();
        for (ChatLogLine line : lines) {
            if (line.offsetSeconds() >= 0
                    && line.offsetSeconds() <= end
                    && !line.message().isBlank()) {
                kept.add(line);
            }
        }
        if (kept.isEmpty()) {
            throw new IllegalArgumentException(
                    "every line of the chat log falls outside the recording; check the log's date and time zone");
        }
        if (kept.size() > MAX_LINES) {
            throw new IllegalArgumentException("the chat log has more than " + MAX_LINES + " lines");
        }
        kept.sort(Comparator.comparingDouble(ChatLogLine::offsetSeconds));
        return List.copyOf(kept);
    }

    private List<ChatLogLine> parseJson(String text, long recordingStartMs) {
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception exception) {
            throw new IllegalArgumentException("the chat log is not valid JSON: " + exception.getMessage());
        }
        JsonNode items = root;
        if (root.isObject()) {
            items = root.has("comments") ? root.get("comments") : root.get("messages");
        }
        if (items == null || !items.isArray()) {
            throw new IllegalArgumentException("the JSON chat log holds no array of comments or messages");
        }
        List<ChatLogLine> lines = new ArrayList<>();
        for (JsonNode item : items) {
            if (!item.isObject()) {
                continue;
            }
            Optional<Double> offset = firstNumber(item, OFFSET_KEYS).or(() -> firstText(item, TIMESTAMP_KEYS)
                    .flatMap(value -> offsetFromTimestamp(value, recordingStartMs)));
            String user = firstUser(item);
            String message = firstMessage(item);
            if (offset.isPresent() && user != null && message != null) {
                lines.add(new ChatLogLine(offset.get(), user, message));
            }
        }
        return lines;
    }

    private boolean looksLikeTable(String text) {
        String header = text.lines().findFirst().orElse("").toLowerCase(Locale.ROOT);
        boolean delimited = header.contains(",") || header.contains("\t");
        boolean named = (OFFSET_KEYS.stream().anyMatch(header::contains)
                        || TIMESTAMP_KEYS.stream().anyMatch(header::contains))
                && MESSAGE_KEYS.stream().anyMatch(header::contains);
        return delimited && named;
    }

    private List<ChatLogLine> parseTable(String text, long recordingStartMs) {
        List<String> rows = text.lines().toList();
        char delimiter = rows.get(0).contains("\t") ? '\t' : ',';
        List<String> header = splitRow(rows.get(0), delimiter).stream()
                .map(name -> name.strip().toLowerCase(Locale.ROOT))
                .toList();
        int offsetAt = indexOf(header, OFFSET_KEYS);
        int timestampAt = indexOf(header, TIMESTAMP_KEYS);
        int userAt = indexOf(header, USER_KEYS);
        int messageAt = indexOf(header, MESSAGE_KEYS);
        if ((offsetAt < 0 && timestampAt < 0) || userAt < 0 || messageAt < 0) {
            throw new IllegalArgumentException(
                    "the CSV header must name a time (offset or timestamp), a user, and a message column");
        }
        List<ChatLogLine> lines = new ArrayList<>();
        for (String row : rows.subList(1, rows.size())) {
            if (row.isBlank()) {
                continue;
            }
            List<String> cells = splitRow(row, delimiter);
            int needed = Math.max(Math.max(offsetAt, timestampAt), Math.max(userAt, messageAt));
            if (cells.size() <= needed) {
                continue;
            }
            Optional<Double> offset = offsetAt >= 0
                    ? parseOffset(cells.get(offsetAt))
                    : offsetFromTimestamp(cells.get(timestampAt), recordingStartMs);
            String user = cells.get(userAt).strip();
            String message = cells.get(messageAt);
            if (offset.isPresent() && !user.isEmpty()) {
                lines.add(new ChatLogLine(offset.get(), user, message));
            }
        }
        return lines;
    }

    private List<ChatLogLine> parseText(String text, long recordingStartMs, ZoneId zone) {
        LocalDate day = null;
        List<ChatLogLine> lines = new ArrayList<>();
        LocalTime previous = null;
        for (String raw : text.lines().toList()) {
            String line = raw.strip();
            if (line.startsWith("#")) {
                Matcher date = TEXT_HEADER_DATE.matcher(line);
                if (date.find()) {
                    day = LocalDate.parse(date.group(1));
                }
                continue;
            }
            Matcher matcher = TEXT_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            LocalTime time = LocalTime.parse(padHours(matcher.group(1)));
            double offset;
            if (day == null) {
                // No date: the times are read as time into the recording (a log that started with the stream).
                offset = time.toSecondOfDay();
            } else {
                if (previous != null && time.isBefore(previous)) {
                    day = day.plusDays(1);
                }
                long epochMs = ZonedDateTime.of(LocalDateTime.of(day, time), zone)
                        .toInstant()
                        .toEpochMilli();
                offset = (epochMs - recordingStartMs) / 1000.0;
            }
            previous = time;
            lines.add(new ChatLogLine(offset, matcher.group(2), matcher.group(3)));
        }
        return lines;
    }

    private static Optional<Double> firstNumber(JsonNode item, List<String> keys) {
        for (String key : keys) {
            JsonNode value = item.get(key);
            if (value != null && value.isNumber()) {
                return Optional.of(value.asDouble());
            }
            if (value != null && value.isTextual()) {
                Optional<Double> parsed = parseOffset(value.asText());
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> firstText(JsonNode item, List<String> keys) {
        for (String key : keys) {
            JsonNode value = item.get(key);
            if (value != null && (value.isTextual() || value.isNumber())) {
                return Optional.of(value.asText());
            }
        }
        return Optional.empty();
    }

    private static String firstUser(JsonNode item) {
        for (String key : USER_KEYS) {
            JsonNode value = item.get(key);
            if (value == null) {
                continue;
            }
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText().strip();
            }
            if (value.isObject()) {
                for (String inner : List.of("login", "name", "display_name", "displayName", "username")) {
                    JsonNode nested = value.get(inner);
                    if (nested != null && nested.isTextual() && !nested.asText().isBlank()) {
                        return nested.asText().strip();
                    }
                }
            }
        }
        return null;
    }

    private static String firstMessage(JsonNode item) {
        for (String key : MESSAGE_KEYS) {
            JsonNode value = item.get(key);
            if (value == null) {
                continue;
            }
            if (value.isTextual()) {
                return value.asText();
            }
            if (value.isObject()) {
                for (String inner : List.of("body", "text", "content")) {
                    JsonNode nested = value.get(inner);
                    if (nested != null && nested.isTextual()) {
                        return nested.asText();
                    }
                }
                // TwitchDownloader keeps the text in fragments.
                JsonNode fragments = value.get("fragments");
                if (fragments != null && fragments.isArray()) {
                    StringBuilder joined = new StringBuilder();
                    for (JsonNode fragment : fragments) {
                        joined.append(fragment.path("text").asText(""));
                    }
                    return joined.toString();
                }
            }
        }
        return null;
    }

    /** "123.5", "1:02:03", or "02:03" as seconds. */
    static Optional<Double> parseOffset(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Matcher hms = HMS.matcher(value);
        if (hms.matches()) {
            return Optional.of(Integer.parseInt(hms.group(1)) * 3600.0
                    + Integer.parseInt(hms.group(2)) * 60.0
                    + Integer.parseInt(hms.group(3)));
        }
        if (value.matches("^\\d{1,2}:\\d{2}$")) {
            String[] parts = value.split(":");
            return Optional.of(Integer.parseInt(parts[0]) * 60.0 + Integer.parseInt(parts[1]));
        }
        try {
            return Optional.of(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /** An ISO-8601 instant or an epoch (seconds or milliseconds) as seconds into the recording. */
    static Optional<Double> offsetFromTimestamp(String raw, long recordingStartMs) {
        String value = raw == null ? "" : raw.strip();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            long epochMs;
            if (value.matches("^\\d{9,13}$")) {
                long number = Long.parseLong(value);
                epochMs = number < 100_000_000_000L ? number * 1000 : number;
            } else {
                epochMs = Instant.parse(value.replace(' ', 'T')).toEpochMilli();
            }
            return Optional.of((epochMs - recordingStartMs) / 1000.0);
        } catch (DateTimeParseException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static int indexOf(List<String> header, List<String> keys) {
        for (String key : keys) {
            int at = header.indexOf(key.toLowerCase(Locale.ROOT));
            if (at >= 0) {
                return at;
            }
        }
        return -1;
    }

    private static String padHours(String hms) {
        return hms.length() == 7 ? "0" + hms : hms;
    }

    /** A delimited row with quoted fields, doubled quotes inside them, and no line breaks. */
    static List<String> splitRow(String row, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < row.length() && row.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }
}
