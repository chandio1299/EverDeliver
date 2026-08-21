package com.everdeliver.api;

import com.everdeliver.persistence.Notification;
import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private static final String CHANNEL_EMAIL = "email";

    private final NotificationRepository notificationRepository;

    @Transactional
    public Notification createQueued(String recipient, String subject, String body) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        Notification notification = Notification.builder()
                .id(id)
                .channel(CHANNEL_EMAIL)
                .status(NotificationStatus.QUEUED)
                .recipient(recipient)
                .subject(subject)
                .body(body)
                .retryCount(0)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return notificationRepository.save(notification);
    }
}
