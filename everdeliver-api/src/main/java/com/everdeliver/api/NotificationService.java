package com.everdeliver.api;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Transactional(readOnly = true)
    public NotificationStatsResponse stats(Instant since) {
        Map<String, Long> byStatus = zeroStatusCounts();
        long total = 0;
        for (Object[] row : notificationRepository.countGroupedByStatus(since)) {
            NotificationStatus status = (NotificationStatus) row[0];
            long count = ((Number) row[1]).longValue();
            byStatus.put(status.name(), count);
            total += count;
        }

        Map<String, Long> byChannel = zeroChannelCounts();
        for (Object[] row : notificationRepository.countGroupedByChannel(since)) {
            String channel = (String) row[0];
            long count = ((Number) row[1]).longValue();
            byChannel.merge(channel, count, Long::sum);
        }

        Map<String, Long> failuresByChannel = new LinkedHashMap<>();
        for (Object[] row : notificationRepository.countFailuresGroupedByChannel(since)) {
            String channel = (String) row[0];
            long count = ((Number) row[1]).longValue();
            failuresByChannel.merge(channel, count, Long::sum);
        }

        long sent = byStatus.getOrDefault(NotificationStatus.SENT.name(), 0L);
        double successRate = total == 0 ? 0.0 : (double) sent / (double) total;
        Double avgLatencyMs = notificationRepository.averageSentLatencyMs(
                since != null, since != null ? since : Instant.EPOCH);

        return NotificationStatsResponse.builder()
                .since(since)
                .total(total)
                .byStatus(byStatus)
                .byChannel(byChannel)
                .successRate(successRate)
                .avgLatencyMs(avgLatencyMs)
                .failuresByChannel(failuresByChannel)
                .build();
    }

    public NotificationResponse retry(UUID id) {
        boolean claimed = notificationPersistenceService.claimManualRetry(id);
        if (!claimed) {
            if (!notificationRepository.existsById(id)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
            }
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Notification cannot be retried unless FAILED or DEAD");
        }

        Notification notification = notificationRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        NotificationRequest kafkaPayload = toKafkaPayload(notification);
        try {
            kafkaTemplate
                    .send(TOPIC, notification.getId().toString(), kafkaPayload)
                    .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Failed to publish notification to Kafka", ex);
        }

        return toResponse(notification);
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

    private static Map<String, Long> zeroStatusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (NotificationStatus status : NotificationStatus.values()) {
            counts.put(status.name(), 0L);
        }
        return counts;
    }

    private static Map<String, Long> zeroChannelCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Channel channel : Channel.values()) {
            counts.put(channel.getValue(), 0L);
        }
        return counts;
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
