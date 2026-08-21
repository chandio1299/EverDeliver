package com.everdeliver.worker;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedactorTest {

    @Test
    void stripsQueryStringsAndAuthTokens() {
        String input = "POST https://hooks.slack.com/services/T/B/XXX?token=secret Authorization: Bearer abc123 api_key=sg-live failed";
        String redacted = Redactor.redact(input);

        assertThat(redacted).contains("https://hooks.slack.com/services/T/B/XXX?redacted");
        assertThat(redacted).doesNotContain("abc123");
        assertThat(redacted).doesNotContain("sg-live");
        assertThat(redacted).doesNotContain("token=secret");
    }

    @Test
    void nullBecomesUnknownError() {
        assertThat(Redactor.redact(null)).isEqualTo("Unknown error");
    }
}
