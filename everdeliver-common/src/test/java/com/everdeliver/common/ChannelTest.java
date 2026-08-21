package com.everdeliver.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChannelTest {

    @Test
    void parsesKnownValuesCaseInsensitively() {
        assertThat(Channel.fromJson("email")).isEqualTo(Channel.EMAIL);
        assertThat(Channel.fromJson("SMS")).isEqualTo(Channel.SMS);
        assertThat(Channel.fromJson("WhatsApp")).isEqualTo(Channel.WHATSAPP);
    }

    @Test
    void blankDefaultsToEmail() {
        assertThat(Channel.fromJson(null)).isEqualTo(Channel.EMAIL);
        assertThat(Channel.fromJson("  ")).isEqualTo(Channel.EMAIL);
        assertThat(Channel.orEmail(null)).isEqualTo(Channel.EMAIL);
    }

    @Test
    void unknownValueThrows() {
        assertThatThrownBy(() -> Channel.fromJson("fax"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown channel");
    }

    @Test
    void jsonValueIsLowercase() {
        assertThat(Channel.EMAIL.getValue()).isEqualTo("email");
        assertThat(Channel.WEBHOOK.getValue()).isEqualTo("webhook");
    }
}
