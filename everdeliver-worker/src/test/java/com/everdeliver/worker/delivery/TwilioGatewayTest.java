package com.everdeliver.worker.delivery;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.web.client.RestClient;

class TwilioGatewayTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private ProviderProperties properties;
    private TwilioGateway gateway;

    @BeforeEach
    void setUp() {
        properties = new ProviderProperties();
        properties.getTwilio().setAccountSid("ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        properties.getTwilio().setAuthToken("token");
        properties.getTwilio().setSmsFrom("+15557654321");
        properties.getTwilio().setWhatsappFrom("whatsapp:+14155238886");
        properties.getTwilio().setBaseUrl(wireMock.baseUrl());
        gateway = new TwilioGateway(properties, TestRestClients.http1());
    }

    @Test
    void smsSuccessReturnsSid() {
        stubMessageCreated("SM123");

        DeliveryResult result = gateway.sendSms("+15551234567", "Hello");

        assertThat(result.providerMessageId()).isEqualTo("SM123");
        wireMock.verify(postRequestedFor(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .withRequestBody(containing("To=%2B15551234567")));
    }

    @Test
    void whatsappPrefixesAddresses() {
        stubMessageCreated("MM999");

        DeliveryResult result = gateway.sendWhatsApp("+15551234567", "Hello WA");

        assertThat(result.providerMessageId()).isEqualTo("MM999");
        wireMock.verify(postRequestedFor(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .withRequestBody(containing("whatsapp%3A%2B15551234567"))
                .withRequestBody(containing("whatsapp%3A%2B14155238886")));
    }

    @Test
    void twilio400IsPermanent() {
        wireMock.stubFor(post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse().withStatus(400).withBody("{\"code\":21211,\"message\":\"Invalid\"}")));

        assertThatThrownBy(() -> gateway.sendSms("+15551234567", "Hello"))
                .isInstanceOf(PermanentDeliveryException.class);
    }

    @Test
    void twilio500IsRetryable() {
        wireMock.stubFor(post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse().withStatus(500).withBody("{\"message\":\"down\"}")));

        assertThatThrownBy(() -> gateway.sendSms("+15551234567", "Hello"))
                .isInstanceOf(RetryableDeliveryException.class);
    }

    @Test
    void missingConfigIsPermanent() {
        ProviderProperties empty = new ProviderProperties();
        TwilioGateway unconfigured = new TwilioGateway(empty, RestClient.builder().build());
        assertThatThrownBy(() -> unconfigured.sendSms("+15551234567", "Hi"))
                .isInstanceOf(PermanentDeliveryException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void smsSenderDelegates() {
        SmsSender sender = new SmsSender(gateway);
        assertThat(sender.channel()).isEqualTo(Channel.SMS);
        stubMessageCreated("SM1");
        NotificationRequest request = new NotificationRequest();
        request.setId(UUID.randomUUID());
        request.setRecipient("+15551234567");
        request.setMessage("Hi");
        assertThat(sender.send(request).providerMessageId()).isEqualTo("SM1");
    }

    private void stubMessageCreated(String sid) {
        wireMock.stubFor(post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\":\"" + sid + "\",\"status\":\"queued\"}")));
    }
}
