package com.streamsense.analyticsservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatSignalsTest {

    @Test
    void aLinkAtTheEndOfASentenceKeepsItsHost() {
        assertThat(ChatSignals.linkHosts("Visit https://redbull.com.")).containsExactly("redbull.com");
        assertThat(ChatSignals.linkHosts("see https://www.RedBull.com/f1, then http://a.b-"))
                .containsExactly("redbull.com", "a.b");
        assertThat(ChatSignals.linkHosts("no links here")).isEmpty();
        assertThat(ChatSignals.normalizeHost("https://www.RedBull.com/f1")).isEqualTo("redbull.com");
    }

    @Test
    void commandsAreTheFirstWordWithABang() {
        assertThat(ChatSignals.command("!RedBull now")).isEqualTo("!redbull");
        assertThat(ChatSignals.command("say !redbull")).isNull();
        assertThat(ChatSignals.normalizeCommand("RedBull")).isEqualTo("!redbull");
    }
}
