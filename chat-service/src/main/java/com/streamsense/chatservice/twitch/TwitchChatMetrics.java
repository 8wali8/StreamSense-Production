package com.streamsense.chatservice.twitch;

import com.streamsense.chatservice.config.StreamSenseProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class TwitchChatMetrics {

    private final boolean enabled;
    private final AtomicReference<List<String>> channels;
    private final Counter messages;
    private final Counter reconnects;
    private final Counter errors;
    private final Counter parseFailures;
    private final Counter duplicates;
    private final AtomicReference<TwitchChatState> state = new AtomicReference<>(TwitchChatState.STOPPED);
    private final AtomicReference<String> lastError = new AtomicReference<>();
    private final AtomicLong lastMessageAt = new AtomicLong(0);
    private final AtomicLong reconnectAttempts = new AtomicLong(0);

    public TwitchChatMetrics(MeterRegistry meterRegistry, StreamSenseProperties properties) {
        StreamSenseProperties.Chat chat = properties.getTwitch().getChat();
        this.enabled = chat.isEnabled();
        this.channels = new AtomicReference<>(normalizeChannels(chat.getChannels()));

        this.messages = Counter.builder("streamsense_twitch_chat_messages_total")
                .description("Total Twitch chat messages accepted into StreamSense")
                .register(meterRegistry);
        this.reconnects = Counter.builder("streamsense_twitch_chat_reconnects_total")
                .description("Total Twitch chat reconnect attempts")
                .register(meterRegistry);
        this.errors = Counter.builder("streamsense_twitch_chat_errors_total")
                .description("Total Twitch chat connector errors")
                .register(meterRegistry);
        this.parseFailures = Counter.builder("streamsense_twitch_chat_parse_failures_total")
                .description("Total Twitch IRC parse failures")
                .register(meterRegistry);
        this.duplicates = Counter.builder("streamsense_twitch_chat_duplicates_total")
                .description("Total duplicate Twitch chat messages ignored")
                .register(meterRegistry);

        Gauge.builder("streamsense_twitch_chat_connected", this, metrics -> metrics.isConnected() ? 1.0 : 0.0)
                .description("Whether Twitch chat ingestion is currently connected")
                .register(meterRegistry);
        Gauge.builder(
                        "streamsense_twitch_chat_last_message_age_seconds",
                        this,
                        TwitchChatMetrics::lastMessageAgeSeconds)
                .description("Seconds since the last accepted Twitch chat message, or -1 when none has been received")
                .register(meterRegistry);
    }

    public void markDisabled() {
        state.set(TwitchChatState.DISABLED);
    }

    public void markConnecting() {
        state.set(TwitchChatState.CONNECTING);
        lastError.set(null);
    }

    public void markConnected() {
        state.set(TwitchChatState.CONNECTED);
        lastError.set(null);
    }

    public void markReconnecting() {
        state.set(TwitchChatState.RECONNECTING);
        reconnectAttempts.incrementAndGet();
        reconnects.increment();
    }

    public void markStopped() {
        state.set(TwitchChatState.STOPPED);
    }

    public void markFailed(String message) {
        state.set(TwitchChatState.FAILED);
        lastError.set(message);
        errors.increment();
    }

    public void recordMessage() {
        lastMessageAt.set(Instant.now().toEpochMilli());
        messages.increment();
    }

    public void recordParseFailure() {
        parseFailures.increment();
    }

    public void recordDuplicate() {
        duplicates.increment();
    }

    public TwitchChatStatus snapshot() {
        return new TwitchChatStatus(
                enabled, state.get(), channels.get(), lastMessageAt.get(), lastError.get(), reconnectAttempts.get());
    }

    public void setChannels(List<String> channels) {
        this.channels.set(normalizeChannels(channels));
    }

    private boolean isConnected() {
        return state.get() == TwitchChatState.CONNECTED;
    }

    private double lastMessageAgeSeconds() {
        long last = lastMessageAt.get();
        if (last == 0) {
            return -1.0;
        }
        return Math.max(0.0, (Instant.now().toEpochMilli() - last) / 1000.0);
    }

    private static List<String> normalizeChannels(List<String> channels) {
        if (channels == null) {
            return List.of();
        }
        return channels.stream()
                .filter(channel -> channel != null && !channel.isBlank())
                .map(channel -> channel.trim().toLowerCase(Locale.ROOT))
                .map(channel -> channel.startsWith("#") ? channel.substring(1) : channel)
                .toList();
    }
}
