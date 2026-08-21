package com.everdeliver.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPersistenceService notificationPersistenceService;

    @Mock
    private KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, notificationPersistenceService, kafkaTemplate);
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
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        NotificationRequest request = new NotificationRequest();
        request.setChannel(Channel.SMS);
        request.setPhone("+15551234567");
        request.setMessage("Hi");

        NotificationQueuedResponse response = notificationService.enqueue(request);

        assertThat(response.getId()).isEqualTo(id);
        assertThat(response.getStatus()).isEqualTo(NotificationStatus.QUEUED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ProducerRecord<String, NotificationRequest>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, NotificationRequest> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("notification-topic");
        assertThat(record.key()).isEqualTo(id.toString());
        assertThat(record.value().getChannel()).isEqualTo(Channel.SMS);
        assertThat(record.value().getRecipient()).isEqualTo("+15551234567");
        assertThat(record.value().getEmail()).isNull();
        assertThat(new String(record.headers().lastHeader("channel").value())).isEqualTo("sms");
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
}
