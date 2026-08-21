package com.everdeliver.api;

import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String TOPIC = "notification-topic";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    private final NotificationRepository notificationRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    public NotificationQueuedResponse enqueue(NotificationRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email is required");
        }
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }

        Notification notification = notificationPersistenceService.createQueued(
                request.getEmail().trim(),
                request.getSubject(),
                request.getMessage());

        NotificationRequest kafkaPayload = new NotificationRequest(
                notification.getId(),
                notification.getRecipient(),
                notification.getBody(),
                notification.getSubject());

        try {
            kafkaTemplate.send(TOPIC, notification.getId().toString(), kafkaPayload)
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to publish notification to Kafka",
                    ex);
        }

        return new NotificationQueuedResponse(notification.getId(), NotificationStatus.QUEUED);
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return toResponse(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(NotificationStatus status, Instant since, Integer limit) {
        int pageSize = normalizeLimit(limit);
        var pageable = PageRequest.of(0, pageSize);

        List<Notification> notifications;
        if (status != null && since != null) {
            notifications = notificationRepository.findByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                    status, since, pageable);
        } else if (status != null) {
            notifications = notificationRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else if (since != null) {
            notifications = notificationRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since, pageable);
        } else {
            notifications = notificationRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return notifications.stream().map(this::toResponse).toList();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .status(notification.getStatus())
                .channel(notification.getChannel())
                .recipient(notification.getRecipient())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .providerMessageId(notification.getProviderMessageId())
                .retryCount(notification.getRetryCount())
                .lastError(notification.getLastError())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .sentAt(notification.getSentAt())
                .build();
    }
}
