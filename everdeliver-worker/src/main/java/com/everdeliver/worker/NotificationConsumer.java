package com.everdeliver.worker;

import com.everdeliver.common.NotificationRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationConsumer {

    private final JavaMailSender mailSender;
    private final NotificationStatusService notificationStatusService;

    @KafkaListener(topics = "notification-topic", groupId = "everdeliver-group")
    public void consume(NotificationRequest request) {
        if (request.getId() == null) {
            log.warn("Skipping message without notification id");
            return;
        }

        UUID id = request.getId();

        if (!notificationStatusService.claimForProcessing(id)) {
            log.info("Notification {} not in QUEUED state; skipping (likely redelivery)", id);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@everdeliver.com");
            message.setTo(request.getEmail());
            message.setSubject(request.getSubject());
            message.setText(request.getMessage());

            mailSender.send(message);

            if (!notificationStatusService.markSent(id)) {
                log.warn("Notification {} could not transition PROCESSING → SENT", id);
            } else {
                log.info("Email successfully sent for notification {}", id);
            }
        } catch (Exception ex) {
            // Swallow so the Kafka offset commits; retry/DLQ is Phase 2.
            if (!notificationStatusService.markFailed(id, ex.getMessage())) {
                log.warn("Notification {} could not transition PROCESSING → FAILED", id);
            }
            log.error("Failed to send notification {}: {}", id, ex.getMessage());
        }
    }
}
