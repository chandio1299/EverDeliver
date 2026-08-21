package com.everdeliver.worker;

import com.everdeliver.common.Channel;
import com.everdeliver.common.NotificationRequest;
import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Requeues notifications stuck in PROCESSING (e.g. worker crash after claim) and republishes
 * them so delivery can complete. Claim guards alone skip redelivered Kafka records and would
 * otherwise leave rows stuck forever.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationReaper {

    static final String TOPIC = "notification-topic";
    private static final long KAFKA_SEND_TIMEOUT_SECONDS = 5;

    private final NotificationRepository notificationRepository;
    private final NotificationStatusService notificationStatusService;
    private final DeliveryProperties deliveryProperties;
    private final KafkaTemplate<String, NotificationRequest> kafkaTemplate;

    @Scheduled(fixedDelayString = "${everdeliver.delivery.reaper-interval-ms:30000}")
    public void reapStaleProcessing() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(deliveryProperties.getProcessingTimeout());
        List<Notification> stale = notificationRepository.findByStatusAndUpdatedAtBefore(
                NotificationStatus.PROCESSING, cutoff);
        if (stale.isEmpty()) {
            return;
        }

        List<Notification> requeued = new ArrayList<>();
        for (Notification notification : stale) {
            if (notificationStatusService.requeueStaleProcessing(notification.getId(), cutoff)) {
                requeued.add(notification);
            }
        }

        int published = 0;
        for (Notification notification : requeued) {
            try {
                kafkaTemplate
                        .send(TOPIC, notification.getId().toString(), toKafkaPayload(notification))
                        .get(KAFKA_SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                published++;
            } catch (Exception ex) {
                log.warn(
                        "Requeued notification {} to QUEUED but failed to republish to Kafka: {}",
                        notification.getId(),
                        ex.toString());
            }
        }

        if (published > 0) {
            log.info("Reaped {} stale PROCESSING notification(s) older than {}", published, cutoff);
        }
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
}
