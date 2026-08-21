package com.everdeliver.worker.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.PermanentDeliveryException;
import com.everdeliver.worker.ProviderProperties;
import com.everdeliver.worker.RetryableDeliveryException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

class EmailSenderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ProviderProperties properties;
    private JavaMailSender mailSender;
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        properties = new ProviderProperties();
        mailSender = mock(JavaMailSender.class);
        emailSender = new EmailSender(properties, mailSender, TestRestClients.http1());
    }

    @Test
    void fallsBackToMailWhenSendGridKeyMissing() {
        NotificationRequest request = emailRequest("user@example.com", "Hi", "Hello");

        DeliveryResult result = emailSender.send(request);

        assertThat(result.providerMessageId()).isNull();
        verify(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
    }

    @Test
    void sendGridSuccessPersistsMessageId() {
        properties.getSendgrid().setApiKey("sg-test");
        properties.getSendgrid().setFromEmail("from@example.com");
        properties.getSendgrid().setBaseUrl(wireMock.baseUrl());

        wireMock.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse().withStatus(202).withHeader("X-Message-Id", "sg-msg-9")));

        DeliveryResult result = emailSender.send(emailRequest("user@example.com", "Hi", "Hello"));

        assertThat(result.providerMessageId()).isEqualTo("sg-msg-9");
        wireMock.verify(postRequestedFor(urlEqualTo("/v3/mail/send"))
                .withHeader("Authorization", equalTo("Bearer sg-test")));
    }

    @Test
    void sendGrid400IsPermanent() {
        properties.getSendgrid().setApiKey("sg-test");
        properties.getSendgrid().setFromEmail("from@example.com");
        properties.getSendgrid().setBaseUrl(wireMock.baseUrl());
        wireMock.stubFor(post(urlEqualTo("/v3/mail/send")).willReturn(aResponse().withStatus(400).withBody("bad")));

        assertThatThrownBy(() -> emailSender.send(emailRequest("user@example.com", "Hi", "Hello")))
                .isInstanceOf(PermanentDeliveryException.class)
                .hasMessageContaining("HTTP 400");
    }

    @Test
    void sendGrid500IsRetryable() {
        properties.getSendgrid().setApiKey("sg-test");
        properties.getSendgrid().setFromEmail("from@example.com");
        properties.getSendgrid().setBaseUrl(wireMock.baseUrl());
        wireMock.stubFor(post(urlEqualTo("/v3/mail/send")).willReturn(aResponse().withStatus(500).withBody("oops")));

        assertThatThrownBy(() -> emailSender.send(emailRequest("user@example.com", "Hi", "Hello")))
                .isInstanceOf(RetryableDeliveryException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void sendGridWithoutFromIsPermanent() {
        properties.getSendgrid().setApiKey("sg-test");
        assertThatThrownBy(() -> emailSender.send(emailRequest("user@example.com", "Hi", "Hello")))
                .isInstanceOf(PermanentDeliveryException.class)
                .hasMessageContaining("SENDGRID_FROM_EMAIL");
    }

    @Test
    void channelIsEmail() {
        assertThat(emailSender.channel()).isEqualTo(Channel.EMAIL);
    }

    private static NotificationRequest emailRequest(String email, String subject, String message) {
        NotificationRequest request = new NotificationRequest();
        request.setId(UUID.randomUUID());
        request.setChannel(Channel.EMAIL);
        request.setEmail(email);
        request.setRecipient(email);
        request.setSubject(subject);
        request.setMessage(message);
        return request;
    }
}
