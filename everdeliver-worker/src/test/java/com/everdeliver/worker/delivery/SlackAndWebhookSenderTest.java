package com.everdeliver.worker.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.worker.PermanentDeliveryException;
import com.everdeliver.worker.RetryableDeliveryException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SlackAndWebhookSenderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private SlackSender slackSender;
    private WebhookSender webhookSender;

    @BeforeEach
    void setUp() {
        slackSender = new SlackSender(TestRestClients.http1());
        webhookSender = new WebhookSender(TestRestClients.http1());
    }

    @Test
    void slackPostsTextPayload() {
        wireMock.stubFor(post(urlEqualTo("/slack")).willReturn(aResponse().withStatus(200)));

        NotificationRequest request = new NotificationRequest();
        request.setId(UUID.randomUUID());
        request.setChannel(Channel.SLACK);
        request.setRecipient(wireMock.baseUrl() + "/slack");
        request.setSubject("Hello");
        request.setMessage("World");

        DeliveryResult result = slackSender.send(request);

        assertThat(result.providerMessageId()).isNull();
        wireMock.verify(postRequestedFor(urlEqualTo("/slack"))
                .withRequestBody(equalToJson("{\"text\":\"Hello\\nWorld\"}")));
    }

    @Test
    void webhookPostsCanonicalPayloadAndTreats4xxAsPermanent() {
        UUID id = UUID.randomUUID();
        wireMock.stubFor(post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(200)));

        NotificationRequest request = new NotificationRequest();
        request.setId(id);
        request.setChannel(Channel.WEBHOOK);
        request.setRecipient(wireMock.baseUrl() + "/hook");
        request.setSubject("Subj");
        request.setMessage("Body");

        webhookSender.send(request);

        wireMock.verify(postRequestedFor(urlEqualTo("/hook"))
                .withRequestBody(equalToJson("{"
                        + "\"id\":\"" + id + "\","
                        + "\"channel\":\"webhook\","
                        + "\"recipient\":\"" + wireMock.baseUrl() + "/hook\","
                        + "\"subject\":\"Subj\","
                        + "\"message\":\"Body\""
                        + "}")));

        wireMock.stubFor(post(urlEqualTo("/bad")).willReturn(aResponse().withStatus(404)));
        NotificationRequest bad = new NotificationRequest();
        bad.setId(UUID.randomUUID());
        bad.setRecipient(wireMock.baseUrl() + "/bad");
        bad.setMessage("nope");
        assertThatThrownBy(() -> webhookSender.send(bad)).isInstanceOf(PermanentDeliveryException.class);
    }

    @Test
    void webhook429IsRetryable() {
        wireMock.stubFor(post(urlEqualTo("/slow")).willReturn(aResponse().withStatus(429)));
        NotificationRequest request = new NotificationRequest();
        request.setId(UUID.randomUUID());
        request.setRecipient(wireMock.baseUrl() + "/slow");
        request.setMessage("hi");
        assertThatThrownBy(() -> webhookSender.send(request)).isInstanceOf(RetryableDeliveryException.class);
    }
}
