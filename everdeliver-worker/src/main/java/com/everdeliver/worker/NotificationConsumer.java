package com.everdeliver.worker;

import com.everdeliver.common.NotificationRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {

    static final String MAIN_TOPIC = "notification-topic";

    private final JavaMailSender mailSender;
    private final NotificationStatusService notificationStatusService;
    private final DeliveryExceptionClassifier deliveryExceptionClassifier;
    private final DeliveryProperties deliveryProperties;

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delay = 5000, multiplier = 6.0, maxDelay = 120000),
            dltTopicSuffix = "-dlq",
            exclude = PermanentDeliveryException.class)
    @KafkaListener(topics = MAIN_TOPIC, groupId = "everdeliver-group")
    public void consume(NotificationRequest request, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        if (request.getId() == null) {
            log.warn("Skipping message without notification id from topic {}", topic);
            return;
        }

        UUID id = request.getId();
        boolean claimed = isRetryTopic(topic)
                ? notificationStatusService.claimRetry(id)
                : notificationStatusService.claimForProcessing(id);

        if (!claimed) {
            log.info("Notification {} not claimable from topic {}; skipping", id, topic);
            return;
        }

        try {
            deliver(request);
            if (!notificationStatusService.markSent(id)) {
                log.warn("Notification {} could not transition PROCESSING → SENT", id);
            } else {
                log.info("Email successfully sent for notification {}", id);
            }
        } catch (PermanentDeliveryException | RetryableDeliveryException ex) {
            if (!notificationStatusService.markFailed(id, ex.getMessage())) {
                log.warn("Notification {} could not transition PROCESSING → FAILED", id);
            }
            log.error("Failed to send notification {}: {}", id, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            RuntimeException classified = deliveryExceptionClassifier.classify(ex);
            if (!notificationStatusService.markFailed(id, classified.getMessage())) {
                log.warn("Notification {} could not transition PROCESSING → FAILED", id);
            }
            log.error("Failed to send notification {}: {}", id, classified.getMessage());
            throw classified;
        }
    }

    @DltHandler
    public void handleDlt(NotificationRequest request, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        if (request == null || request.getId() == null) {
            log.warn("DLT message on {} missing notification id", topic);
            return;
        }

        UUID id = request.getId();
        if (!notificationStatusService.markDead(id)) {
            log.warn("Notification {} could not transition FAILED → DEAD from {}", id, topic);
        } else {
            log.error("Notification {} marked DEAD after retries exhausted or permanent failure ({})", id, topic);
        }
    }

    private void deliver(NotificationRequest request) {
        if (deliveryProperties.isSimulatePermanentFailure()) {
            throw new PermanentDeliveryException("Simulated permanent provider failure");
        }
        if (deliveryProperties.isSimulateFailure()) {
            throw new RetryableDeliveryException("Simulated retryable provider failure");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("noreply@everdeliver.com");
        message.setTo(request.getEmail());
        message.setSubject(request.getSubject());
        message.setText(request.getMessage());
        mailSender.send(message);
    }

    static boolean isRetryTopic(String topic) {
        return topic != null && topic.startsWith(MAIN_TOPIC + "-retry");
    }
}
