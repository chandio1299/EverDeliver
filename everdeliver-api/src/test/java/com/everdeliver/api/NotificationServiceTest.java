package com.everdeliver.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPersistenceService notificationPersistenceService;

    @Mock
    private KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    private ApiDeliveryProperties deliveryProperties;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        deliveryProperties = new ApiDeliveryProperties();
        notificationService = new NotificationService(
                notificationRepository, notificationPersistenceService, kafkaTemplate, deliveryProperties);
    }

    @Test
    void enqueuePersistsChannelAndPublishesRecipientPayload() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Notification saved = Notification.builder()
                .id(id)
                .channel("sms")
                .status(NotificationStatus.QUEUED)
                .recipient("+15551234567")
                .body("Hi")
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(notificationPersistenceService.createQueued("sms", "+15551234567", null, "Hi"))
                .thenReturn(saved);
        when(kafkaTemplate.send(any(String.class), any(String.class), any(NotificationRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        NotificationRequest request = new NotificationRequest();
        request.setChannel(Channel.SMS);
        request.setPhone("+15551234567");
        request.setMessage("Hi");

        NotificationQueuedResponse response = notificationService.enqueue(request);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.QUEUED);

        ArgumentCaptor<NotificationRequest> payloadCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(kafkaTemplate).send(any(String.class), any(String.class), payloadCaptor.capture());
        NotificationRequest payload = payloadCaptor.getValue();
        assertThat(payload.getChannel()).isEqualTo(Channel.SMS);
        assertThat(payload.getRecipient()).isEqualTo("+15551234567");
        assertThat(payload.getEmail()).isNull();
    }

    @Test
    void emailKafkaPayloadKeepsEmailForPhase2Compatibility() {
        UUID id = UUID.randomUUID();
        Notification saved = Notification.builder()
                .id(id)
                .channel("email")
                .status(NotificationStatus.QUEUED)
                .recipient("user@example.com")
                .subject("Hi")
                .body("Hello")
                .retryCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        NotificationRequest payload = NotificationService.toKafkaPayload(saved);

        assertThat(payload.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(payload.getRecipient()).isEqualTo("user@example.com");
        assertThat(payload.getEmail()).isEqualTo("user@example.com");
        assertThat(payload.getMessage()).isEqualTo("Hello");
    }

    @Test
    void getByIdMasksSlackAndWebhookRecipientButNotEmail() {
        Instant now = Instant.now();
        UUID slackId = UUID.randomUUID();
        when(notificationRepository.findById(slackId))
                .thenReturn(Optional.of(Notification.builder()
                        .id(slackId)
                        .channel("slack")
                        .status(NotificationStatus.QUEUED)
                        .recipient("https://hooks.slack.com/services/T00/B00/secret-token")
                        .body("Hi")
                        .retryCount(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));

        assertThat(notificationService.getById(slackId).getRecipient())
                .isEqualTo("https://hooks.slack.com/***");

        UUID webhookId = UUID.randomUUID();
        when(notificationRepository.findById(webhookId))
                .thenReturn(Optional.of(Notification.builder()
                        .id(webhookId)
                        .channel("webhook")
                        .status(NotificationStatus.QUEUED)
                        .recipient("https://example.com/hooks/abc?token=secret")
                        .body("Hi")
                        .retryCount(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));

        assertThat(notificationService.getById(webhookId).getRecipient())
                .isEqualTo("https://example.com/***");

        UUID emailId = UUID.randomUUID();
        when(notificationRepository.findById(emailId))
                .thenReturn(Optional.of(Notification.builder()
                        .id(emailId)
                        .channel("email")
                        .status(NotificationStatus.QUEUED)
                        .recipient("user@example.com")
                        .body("Hi")
                        .retryCount(0)
                        .createdAt(now)
                        .updatedAt(now)
                        .build()));

        assertThat(notificationService.getById(emailId).getRecipient()).isEqualTo("user@example.com");
    }

    @Test
    void retryPublishesWhenFailedAndKeepsRetryCount() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Notification queued = Notification.builder()
                .id(id)
                .channel("email")
                .status(NotificationStatus.QUEUED)
                .recipient("user@example.com")
                .subject("Hi")
                .body("Hello")
                .retryCount(3)
                .lastError(null)
                .createdAt(now)
                .updatedAt(now)
                .build();

        when(notificationPersistenceService.claimManualRetry(id)).thenReturn(true);
        when(notificationRepository.findById(id)).thenReturn(Optional.of(queued));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(NotificationRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        NotificationResponse response = notificationService.retry(id);

        assertThat(response.getStatus()).isEqualTo(NotificationStatus.QUEUED);
        assertThat(response.getRetryCount()).isEqualTo(3);
        ArgumentCaptor<NotificationRequest> payloadCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(kafkaTemplate).send(any(String.class), any(String.class), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getId()).isEqualTo(id);
        assertThat(payloadCaptor.getValue().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void retryConflictWhenNotFailedOrDead() {
        UUID id = UUID.randomUUID();
        when(notificationPersistenceService.claimManualRetry(id)).thenReturn(false);
        when(notificationRepository.existsById(id)).thenReturn(true);

        assertThatThrownBy(() -> notificationService.retry(id))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any(NotificationRequest.class));
    }

    @Test
    void retryNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(notificationPersistenceService.claimManualRetry(id)).thenReturn(false);
        when(notificationRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.retry(id))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void statsAggregatesSuccessRateAndLatency() {
        Instant since = Instant.parse("2026-08-21T00:00:00Z");
        when(notificationRepository.countGroupedByStatus(since))
                .thenReturn(List.of(
                        new Object[] {NotificationStatus.SENT, 2L},
                        new Object[] {NotificationStatus.DEAD, 2L}));
        when(notificationRepository.countGroupedByChannel(since))
                .thenReturn(List.of(
                        new Object[] {"email", 3L},
                        new Object[] {"sms", 1L}));
        when(notificationRepository.countFailuresGroupedByChannel(since))
                .thenReturn(List.of(new Object[] {"sms", 1L}, new Object[] {"email", 1L}));
        when(notificationRepository.averageSentLatencyMs(true, since)).thenReturn(150.5);

        NotificationStatsResponse stats = notificationService.stats(since);

        assertThat(stats.getTotal()).isEqualTo(4);
        assertThat(stats.getSuccessRate()).isEqualTo(0.5);
        assertThat(stats.getAvgLatencyMs()).isEqualTo(150.5);
        assertThat(stats.getByStatus().get("QUEUED")).isZero();
        assertThat(stats.getByStatus().get("SENT")).isEqualTo(2);
        assertThat(stats.getByChannel().get("email")).isEqualTo(3);
        assertThat(stats.getByChannel().get("whatsapp")).isZero();
        assertThat(stats.getFailuresByChannel()).containsEntry("sms", 1L);
        assertThat(stats.getSince()).isEqualTo(since);
    }
}
