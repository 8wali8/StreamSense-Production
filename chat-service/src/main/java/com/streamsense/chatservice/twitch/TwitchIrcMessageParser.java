package com.streamsense.chatservice.twitch;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TwitchIrcMessageParser {

    public Optional<TwitchIrcChatMessage> parseChatMessage(String line, long receivedAt) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }

        String rest = line;
        Map<String, String> tags = Map.of();
        if (rest.startsWith("@")) {
            int tagEnd = rest.indexOf(' ');
            if (tagEnd < 0) {
                return Optional.empty();
            }
            tags = parseTags(rest.substring(1, tagEnd));
            rest = rest.substring(tagEnd + 1);
        }

        String prefix = "";
        if (rest.startsWith(":")) {
            int prefixEnd = rest.indexOf(' ');
            if (prefixEnd < 0) {
                return Optional.empty();
            }
            prefix = rest.substring(1, prefixEnd);
            rest = rest.substring(prefixEnd + 1);
        }

        if (!rest.startsWith("PRIVMSG ")) {
            return Optional.empty();
        }

        String afterCommand = rest.substring("PRIVMSG ".length());
        int channelEnd = afterCommand.indexOf(' ');
        if (channelEnd < 0) {
            return Optional.empty();
        }

        String channel = normalizeChannel(afterCommand.substring(0, channelEnd));
        String message = afterCommand.substring(channelEnd + 1);
        if (message.startsWith(":")) {
            message = message.substring(1);
        }

        String user = parseUser(prefix, tags);
        if (channel.isBlank() || user.isBlank() || message.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new TwitchIrcChatMessage(
                channel,
                user,
                message,
                blankToNull(tags.get("id")),
                parseTimestamp(tags.get("tmi-sent-ts"), receivedAt)));
    }

    public boolean isPing(String line) {
        return line != null && line.startsWith("PING");
    }

    private static Map<String, String> parseTags(String tagSection) {
        Map<String, String> tags = new HashMap<>();
        for (String pair : tagSection.split(";")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                tags.put(pair, "");
            } else {
                tags.put(pair.substring(0, separator), unescapeTagValue(pair.substring(separator + 1)));
            }
        }
        return tags;
    }

    private static String parseUser(String prefix, Map<String, String> tags) {
        String login = blankToNull(tags.get("login"));
        if (login != null) {
            return login;
        }

        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        int bang = prefix.indexOf('!');
        if (bang > 0) {
            return prefix.substring(0, bang);
        }
        return prefix;
    }

    private static String normalizeChannel(String channel) {
        String normalized = channel == null ? "" : channel.trim().toLowerCase();
        if (normalized.startsWith("#")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    private static long parseTimestamp(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // One pass so an escaped backslash is consumed before the character after it is examined; chained
    // replaces turned "\\s" into backslash-space instead of backslash-s.
    static String unescapeTagValue(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (++i >= value.length()) {
                break;
            }
            switch (value.charAt(i)) {
                case 's' -> out.append(' ');
                case ':' -> out.append(';');
                case 'r' -> out.append('\r');
                case 'n' -> out.append('\n');
                case '\\' -> out.append('\\');
                default -> out.append(value.charAt(i));
            }
        }
        return out.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
