package com.everdeliver.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NotificationRequestValidatorTest {

    @Test
    void emailDefaultsWhenChannelOmitted() {
        NotificationRequest request = new NotificationRequest();
        request.setEmail("user@example.com");
        request.setMessage("Hello");
        request.setSubject("Hi");

        var resolved = NotificationRequestValidator.validate(request);

        assertThat(resolved.channel()).isEqualTo(Channel.EMAIL);
        assertThat(resolved.recipient()).isEqualTo("user@example.com");
        assertThat(resolved.message()).isEqualTo("Hello");
        assertThat(resolved.subject()).isEqualTo("Hi");
    }

    @Test
    void emailRequiredAndMustLookLikeEmail() {
        NotificationRequest missing = new NotificationRequest();
        missing.setMessage("Hello");
        assertThatThrownBy(() -> NotificationRequestValidator.validate(missing))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("email is required");

        NotificationRequest invalid = new NotificationRequest();
        invalid.setEmail("not-an-email");
        invalid.setMessage("Hello");
        assertThatThrownBy(() -> NotificationRequestValidator.validate(invalid))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("email is invalid");
    }

    @Test
    void smsRequiresE164Phone() {
        NotificationRequest request = new NotificationRequest();
        request.setChannel(Channel.SMS);
        request.setPhone("+15551234567");
        request.setMessage("Hi");

        var resolved = NotificationRequestValidator.validate(request);
        assertThat(resolved.channel()).isEqualTo(Channel.SMS);
        assertThat(resolved.recipient()).isEqualTo("+15551234567");

        NotificationRequest bad = new NotificationRequest();
        bad.setChannel(Channel.SMS);
        bad.setPhone("5551234567");
        bad.setMessage("Hi");
        assertThatThrownBy(() -> NotificationRequestValidator.validate(bad))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("E.164");
    }

    @Test
    void slackAndWebhookRequireHttpUrl() {
        NotificationRequest slack = new NotificationRequest();
        slack.setChannel(Channel.SLACK);
        slack.setSlackWebhookUrl("https://hooks.slack.com/services/T/B/XXX");
        slack.setMessage("Hi");
        assertThat(NotificationRequestValidator.validate(slack).recipient())
                .startsWith("https://hooks.slack.com");

        NotificationRequest webhook = new NotificationRequest();
        webhook.setChannel(Channel.WEBHOOK);
        webhook.setWebhookUrl("http://echo-server/hook");
        webhook.setMessage("Hi");
        assertThat(NotificationRequestValidator.validate(webhook).channel()).isEqualTo(Channel.WEBHOOK);

        NotificationRequest ftp = new NotificationRequest();
        ftp.setChannel(Channel.WEBHOOK);
        ftp.setWebhookUrl("ftp://example.com/x");
        ftp.setMessage("Hi");
        assertThatThrownBy(() -> NotificationRequestValidator.validate(ftp))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("http or https");
    }

    @Test
    void messageIsRequiredForEveryChannel() {
        NotificationRequest request = new NotificationRequest();
        request.setEmail("user@example.com");
        assertThatThrownBy(() -> NotificationRequestValidator.validate(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("message is required");
    }

    @Test
    void rejectsOverlongRecipient() {
        NotificationRequest request = new NotificationRequest();
        request.setEmail("a@" + "b".repeat(520) + ".com");
        request.setMessage("Hi");
        assertThatThrownBy(() -> NotificationRequestValidator.validate(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("512");
    }
}
