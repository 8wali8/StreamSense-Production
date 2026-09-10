package com.streamsense.analyticsservice.service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure parsing of chat text into the signals the analytics store counts. */
final class ChatSignals {

    private static final Pattern COMMAND = Pattern.compile("^!([A-Za-z0-9_-]{1,63})(?:\\s|$)");
    private static final Pattern LINK = Pattern.compile("https?://([A-Za-z0-9.-]+)", Pattern.CASE_INSENSITIVE);

    private ChatSignals() {}

    /** The "!command" a message starts with, lower-cased and with the bang, or null. */
    static String command(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = COMMAND.matcher(message.trim());
        return matcher.find() ? "!" + matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /** Distinct link hosts in a message, lower-cased and without a leading "www.". */
    static Set<String> linkHosts(String message) {
        Set<String> hosts = new LinkedHashSet<>();
        if (message == null) {
            return hosts;
        }
        Matcher matcher = LINK.matcher(message);
        while (matcher.find()) {
            String host = matcher.group(1).toLowerCase(Locale.ROOT);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (!host.isBlank() && host.length() <= 255) {
                hosts.add(host);
            }
        }
        return hosts;
    }

    /** Normalises a configured or requested host the same way as {@link #linkHosts}. */
    static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String cleaned = host.trim().toLowerCase(Locale.ROOT);
        cleaned = cleaned.replaceFirst("^https?://", "");
        int slash = cleaned.indexOf('/');
        if (slash >= 0) {
            cleaned = cleaned.substring(0, slash);
        }
        if (cleaned.startsWith("www.")) {
            cleaned = cleaned.substring(4);
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    /** Normalises a configured or requested command: lower-case, with the bang. */
    static String normalizeCommand(String command) {
        if (command == null || command.isBlank()) {
            return null;
        }
        String cleaned = command.trim().toLowerCase(Locale.ROOT);
        return cleaned.startsWith("!") ? cleaned : "!" + cleaned;
    }
}
