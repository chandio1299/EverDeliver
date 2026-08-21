package com.everdeliver.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Optional;

public enum Channel {
    EMAIL("email"),
    SMS("sms"),
    WHATSAPP("whatsapp"),
    SLACK("slack"),
    WEBHOOK("webhook");

    private final String value;

    Channel(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static Optional<Channel> tryParse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (Channel channel : values()) {
            if (channel.value.equals(normalized)) {
                return Optional.of(channel);
            }
        }
        return Optional.empty();
    }

    @JsonCreator
    public static Channel fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMAIL;
        }
        return tryParse(raw)
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel: " + raw));
    }

    public static Channel orEmail(Channel channel) {
        return channel == null ? EMAIL : channel;
    }
}
