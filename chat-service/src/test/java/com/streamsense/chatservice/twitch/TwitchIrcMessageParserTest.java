package com.streamsense.chatservice.twitch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class TwitchIrcMessageParserTest {

    private final TwitchIrcMessageParser parser = new TwitchIrcMessageParser();

    @Test
    void parseChatMessage_extractsTwitchTagsAndPayload() {
        String line = "@badge-info=;badges=;color=;display-name=TestUser;id=abc-123;login=testuser;tmi-sent-ts=1710000000000 "
                + ":testuser!testuser@testuser.tmi.twitch.tv PRIVMSG #SomeChannel :hello from twitch";

        Optional<TwitchIrcChatMessage> parsed = parser.parseChatMessage(line, 1710000009999L);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().channel()).isEqualTo("somechannel");
        assertThat(parsed.get().user()).isEqualTo("testuser");
        assertThat(parsed.get().message()).isEqualTo("hello from twitch");
        assertThat(parsed.get().messageId()).isEqualTo("abc-123");
        assertThat(parsed.get().timestamp()).isEqualTo(1710000000000L);
    }

    @Test
    void parseChatMessage_fallsBackToPrefixAndReceiveTimeWhenTagsMissing() {
        String line = ":someuser!someuser@someuser.tmi.twitch.tv PRIVMSG #channel :message without tags";

        Optional<TwitchIrcChatMessage> parsed = parser.parseChatMessage(line, 1710000009999L);

        assertThat(parsed).isPresent();
        assertThat(parsed.get().channel()).isEqualTo("channel");
        assertThat(parsed.get().user()).isEqualTo("someuser");
        assertThat(parsed.get().message()).isEqualTo("message without tags");
        assertThat(parsed.get().messageId()).isNull();
        assertThat(parsed.get().timestamp()).isEqualTo(1710000009999L);
    }

    @Test
    void parseChatMessage_ignoresNonChatCommands() {
        assertThat(parser.parseChatMessage(":tmi.twitch.tv 001 user :Welcome", 1L)).isEmpty();
        assertThat(parser.parseChatMessage("PING :tmi.twitch.tv", 1L)).isEmpty();
    }

    @Test
    void unescapeTagValue_decodesEscapesInASinglePass() {
        assertThat(TwitchIrcMessageParser.unescapeTagValue("a\\sb")).isEqualTo("a b");
        assertThat(TwitchIrcMessageParser.unescapeTagValue("x\\:y")).isEqualTo("x;y");
        assertThat(TwitchIrcMessageParser.unescapeTagValue("line\\r\\nbreak")).isEqualTo("line\r\nbreak");
        // An escaped backslash followed by 's' is backslash + 's', not a space.
        assertThat(TwitchIrcMessageParser.unescapeTagValue("back\\\\slash")).isEqualTo("back\\slash");
        assertThat(TwitchIrcMessageParser.unescapeTagValue("a\\\\\\sb")).isEqualTo("a\\ b");
        assertThat(TwitchIrcMessageParser.unescapeTagValue("trailing\\")).isEqualTo("trailing");
        assertThat(TwitchIrcMessageParser.unescapeTagValue("unknown\\q")).isEqualTo("unknownq");
        assertThat(TwitchIrcMessageParser.unescapeTagValue("plain")).isEqualTo("plain");
    }

    @Test
    void isPing_detectsTwitchPing() {
        assertThat(parser.isPing("PING :tmi.twitch.tv")).isTrue();
        assertThat(parser.isPing(":user PRIVMSG #channel :PING text")).isFalse();
    }
}
