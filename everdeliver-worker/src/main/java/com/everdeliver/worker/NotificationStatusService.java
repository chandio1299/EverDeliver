package com.everdeliver.worker;

import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationStatusService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public boolean claimForProcessing(UUID id) {
        Instant now = Instant.now();
        return notificationRepository.updateStatusIfCurrent(
                id, NotificationStatus.QUEUED, NotificationStatus.PROCESSING, now) == 1;
    }

    @Transactional
    public boolean claimRetry(UUID id) {
        Instant now = Instant.now();
        return notificationRepository.claimRetryIfCurrent(
                id, NotificationStatus.FAILED, NotificationStatus.PROCESSING, now) == 1;
    }

    @Transactional
    public boolean markSent(UUID id) {
        Instant now = Instant.now();
        return notificationRepository.markSentIfCurrent(
                id, NotificationStatus.PROCESSING, NotificationStatus.SENT, now, now) == 1;
    }

    @Transactional
    public boolean markFailed(UUID id, String lastError) {
        Instant now = Instant.now();
        String truncated = truncate(lastError);
        return notificationRepository.markFailedIfCurrent(
                id, NotificationStatus.PROCESSING, NotificationStatus.FAILED, truncated, now) == 1;
    }

    @Transactional
    public boolean markDead(UUID id) {
        Instant now = Instant.now();
        return notificationRepository.updateStatusIfCurrent(
                id, NotificationStatus.FAILED, NotificationStatus.DEAD, now) == 1;
    }

    private static String truncate(String error) {
        if (error == null) {
            return "Unknown error";
        }
        return error.length() <= 1024 ? error : error.substring(0, 1024);
    }
}
