package com.everdeliver.api;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificationService {

    static final String TOPIC = "notification-topic";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    private final NotificationRepository notificationRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final KafkaTemplate<String, NotificationRequest> kafkaTemplate;
    private final ApiDeliveryProperties deliveryProperties;

    public NotificationQueuedResponse enqueue(NotificationRequest request) {
        NotificationRequestValidator.ResolvedNotification resolved =
                NotificationRequestValidator.validate(request, deliveryProperties.isBlockPrivateHosts());

        Notification notification = notificationPersistenceService.createQueued(
                resolved.channel().getValue(),
                resolved.recipient(),
                resolved.subject(),
                resolved.message());

        NotificationRequest kafkaPayload = toKafkaPayload(notification);

        try {
            kafkaTemplate
                    .send(TOPIC, notification.getId().toString(), kafkaPayload)
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
    public List<NotificationResponse> list(
            NotificationStatus status,
            Instant since,
            Instant updatedSince,
            String channel,
            Integer limit) {
        int pageSize = normalizeLimit(limit);
        String normalizedChannel = normalizeChannel(channel);
        Sort sort = updatedSince != null
                ? Sort.by(Sort.Direction.DESC, "updatedAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        var pageable = PageRequest.of(0, pageSize, sort);

        Specification<Notification> spec = buildListSpec(status, since, updatedSince, normalizedChannel);
        return notificationRepository.findAll(spec, pageable).stream()
                .map(this::toResponse)
                .toList();
    }

    static NotificationRequest toKafkaPayload(Notification notification) {
        NotificationRequest payload = new NotificationRequest();
        payload.setId(notification.getId());
        payload.setChannel(Channel.fromJson(notification.getChannel()));
        payload.setRecipient(notification.getRecipient());
        payload.setSubject(notification.getSubject());
        payload.setMessage(notification.getBody());
        if (payload.getChannel() == Channel.EMAIL) {
            payload.setEmail(notification.getRecipient());
        }
        return payload;
    }

    private static Specification<Notification> buildListSpec(
            NotificationStatus status, Instant since, Instant updatedSince, String channel) {
        List<Specification<Notification>> parts = new ArrayList<>();
        if (status != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (since != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), since));
        }
        if (updatedSince != null) {
            parts.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("updatedAt"), updatedSince));
        }
        if (channel != null) {
            parts.add((root, query, cb) -> cb.equal(root.get("channel"), channel));
        }
        return Specification.allOf(parts);
    }

    private static String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        try {
            return Channel.fromJson(channel.trim()).getValue();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channel is invalid");
        }
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
                .recipient(RecipientMasker.forResponse(notification.getChannel(), notification.getRecipient()))
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
