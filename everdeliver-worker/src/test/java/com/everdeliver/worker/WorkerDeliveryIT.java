package com.everdeliver.worker;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class WorkerDeliveryIT {

    private static final WireMockServer WIRE_MOCK =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        WIRE_MOCK.start();
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine")
            .withDatabaseName("everdeliver")
            .withUsername("everdeliver")
            .withPassword("everdeliver");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("everdeliver.kafka.attempts", () -> "4");
        registry.add("everdeliver.kafka.retry-delay-ms", () -> "200");
        registry.add("everdeliver.kafka.retry-multiplier", () -> "1.0");
        registry.add("everdeliver.kafka.retry-max-delay-ms", () -> "200");
        registry.add("everdeliver.providers.sendgrid.api-key", () -> "sg-test");
        registry.add("everdeliver.providers.sendgrid.from-email", () -> "from@example.com");
        registry.add("everdeliver.providers.sendgrid.base-url", WIRE_MOCK::baseUrl);
        registry.add("everdeliver.providers.twilio.account-sid", () -> "ACaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        registry.add("everdeliver.providers.twilio.auth-token", () -> "token");
        registry.add("everdeliver.providers.twilio.sms-from", () -> "+15557654321");
        registry.add("everdeliver.providers.twilio.whatsapp-from", () -> "whatsapp:+14155238886");
        registry.add("everdeliver.providers.twilio.base-url", WIRE_MOCK::baseUrl);
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    @BeforeEach
    void resetStubs() {
        WIRE_MOCK.resetAll();
    }

    @Test
    void emailSendGridHappyPathPersistsProviderId() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/v3/mail/send"))
                .willReturn(aResponse().withStatus(202).withHeader("X-Message-Id", "sg-it-1")));

        UUID id = enqueue(Channel.EMAIL, "user@example.com", "Hello");

        Notification sent = awaitStatus(id, NotificationStatus.SENT);
        assertThat(sent.getProviderMessageId()).isEqualTo("sg-it-1");
        assertThat(sent.getRetryCount()).isZero();
    }

    @Test
    void smsAndWhatsAppHappyPath() {
        stubTwilio("SMit");
        UUID smsId = enqueue(Channel.SMS, "+15551234567", "SMS hi");
        UUID waId = enqueue(Channel.WHATSAPP, "+15551234567", "WA hi");

        assertThat(awaitStatus(smsId, NotificationStatus.SENT).getProviderMessageId()).isEqualTo("SMit");
        assertThat(awaitStatus(waId, NotificationStatus.SENT).getProviderMessageId()).isEqualTo("SMit");
    }

    @Test
    void slackAndWebhookHappyPath() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/slack")).willReturn(aResponse().withStatus(200)));
        WIRE_MOCK.stubFor(post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(200)));

        UUID slackId = enqueue(Channel.SLACK, WIRE_MOCK.baseUrl() + "/slack", "Slack hi");
        UUID hookId = enqueue(Channel.WEBHOOK, WIRE_MOCK.baseUrl() + "/hook", "Hook hi");

        awaitStatus(slackId, NotificationStatus.SENT);
        awaitStatus(hookId, NotificationStatus.SENT);
    }

    @Test
    void sendGrid4xxGoesDeadWithoutRetries() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/v3/mail/send")).willReturn(aResponse().withStatus(400).withBody("bad")));

        UUID id = enqueue(Channel.EMAIL, "bad@example.com", "nope");
        Notification dead = awaitStatus(id, NotificationStatus.DEAD);
        assertThat(dead.getRetryCount()).isZero();
        assertThat(dead.getLastError()).contains("HTTP 400");
    }

    @Test
    void sendGrid5xxExhaustsRetriesThenDead() {
        WIRE_MOCK.stubFor(post(urlEqualTo("/v3/mail/send")).willReturn(aResponse().withStatus(503).withBody("down")));

        UUID id = enqueue(Channel.EMAIL, "retry@example.com", "later");
        Notification dead = awaitStatus(id, NotificationStatus.DEAD);
        assertThat(dead.getRetryCount()).isEqualTo(3);
        assertThat(dead.getLastError()).contains("HTTP 503");
    }

    private UUID enqueue(Channel channel, String recipient, String message) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        notificationRepository.save(Notification.builder()
                .id(id)
                .channel(channel.getValue())
                .status(NotificationStatus.QUEUED)
                .recipient(recipient)
                .body(message)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build());

        NotificationRequest payload = new NotificationRequest();
        payload.setId(id);
        payload.setChannel(channel);
        payload.setRecipient(recipient);
        if (channel == Channel.EMAIL) {
            payload.setEmail(recipient);
        }
        payload.setMessage(message);
        kafkaTemplate.send(NotificationConsumer.MAIN_TOPIC, id.toString(), payload);
        return id;
    }

    private Notification awaitStatus(UUID id, NotificationStatus expected) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(notificationRepository.findById(id)
                                .map(Notification::getStatus)
                                .orElse(null))
                        .isEqualTo(expected));
        return notificationRepository.findById(id).orElseThrow();
    }

    private void stubTwilio(String sid) {
        WIRE_MOCK.stubFor(post(urlPathMatching("/2010-04-01/Accounts/.*/Messages.json"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sid\":\"" + sid + "\",\"status\":\"queued\"}")));
    }
}
