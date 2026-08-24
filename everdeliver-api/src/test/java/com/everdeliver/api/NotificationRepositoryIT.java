package com.everdeliver.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EntityScan(basePackageClasses = Notification.class)
@EnableJpaRepositories(basePackageClasses = NotificationRepository.class)
class NotificationRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine")
            .withDatabaseName("everdeliver")
            .withUsername("everdeliver")
            .withPassword("everdeliver");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void markSentPersistsProviderMessageId() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        notificationRepository.save(Notification.builder()
                .id(id)
                .channel("email")
                .status(NotificationStatus.PROCESSING)
                .recipient("user@example.com")
                .body("Hello")
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build());

        int updated = notificationRepository.markSentIfCurrent(
                id, NotificationStatus.PROCESSING, NotificationStatus.SENT, now, "sg-msg-1", now);

        assertThat(updated).isEqualTo(1);
        Notification loaded = notificationRepository.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(loaded.getProviderMessageId()).isEqualTo("sg-msg-1");
        assertThat(loaded.getSentAt()).isNotNull();
    }

    @Test
    void claimManualRetryMovesFailedToQueuedAndClearsLastError() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        notificationRepository.save(Notification.builder()
                .id(id)
                .channel("email")
                .status(NotificationStatus.FAILED)
                .recipient("user@example.com")
                .body("Hello")
                .retryCount(2)
                .lastError("timeout")
                .createdAt(now)
                .updatedAt(now)
                .build());

        int updated = notificationRepository.claimManualRetry(id, now.plusSeconds(1));

        assertThat(updated).isEqualTo(1);
        Notification loaded = notificationRepository.findById(id).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(loaded.getLastError()).isNull();
        assertThat(loaded.getRetryCount()).isEqualTo(2);
    }

    @Test
    void claimManualRetryLeavesSentUnchanged() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        notificationRepository.save(Notification.builder()
                .id(id)
                .channel("email")
                .status(NotificationStatus.SENT)
                .recipient("user@example.com")
                .body("Hello")
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .sentAt(now)
                .build());

        assertThat(notificationRepository.claimManualRetry(id, now.plusSeconds(1))).isZero();
        assertThat(notificationRepository.findById(id).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void statsQueriesCountAndLatency() {
        Instant t0 = Instant.parse("2026-08-21T10:00:00Z");
        Instant t1 = Instant.parse("2026-08-21T10:00:01Z");
        notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID())
                .channel("email")
                .status(NotificationStatus.SENT)
                .recipient("a@example.com")
                .body("Hello")
                .retryCount(0)
                .createdAt(t0)
                .updatedAt(t1)
                .sentAt(t1)
                .build());
        notificationRepository.save(Notification.builder()
                .id(UUID.randomUUID())
                .channel("sms")
                .status(NotificationStatus.DEAD)
                .recipient("+15551234567")
                .body("Hello")
                .retryCount(0)
                .lastError("no twilio")
                .createdAt(t0)
                .updatedAt(t1)
                .build());

        assertThat(notificationRepository.countGroupedByStatus(null)).isNotEmpty();
        assertThat(notificationRepository.countFailuresGroupedByChannel(null)).isNotEmpty();
        Double latency = notificationRepository.averageSentLatencyMs(false, Instant.EPOCH);
        assertThat(latency).isNotNull();
        assertThat(latency).isGreaterThanOrEqualTo(0);
    }
}
