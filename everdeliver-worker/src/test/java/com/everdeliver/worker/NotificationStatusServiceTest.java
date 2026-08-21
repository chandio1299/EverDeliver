package com.everdeliver.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.everdeliver.persistence.NotificationRepository;
import com.everdeliver.persistence.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationStatusServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationStatusService statusService;

    @BeforeEach
    void setUp() {
        statusService = new NotificationStatusService(notificationRepository);
    }

    @Test
    void claimForProcessingDoesNotIncrementRetryCount() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.updateStatusIfCurrent(
                        eq(id), eq(NotificationStatus.QUEUED), eq(NotificationStatus.PROCESSING), any(Instant.class)))
                .thenReturn(1);

        assertThat(statusService.claimForProcessing(id)).isTrue();
        verify(notificationRepository).updateStatusIfCurrent(
                eq(id), eq(NotificationStatus.QUEUED), eq(NotificationStatus.PROCESSING), any(Instant.class));
    }

    @Test
    void claimRetryIncrementsRetryCount() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.claimRetryIfCurrent(
                        eq(id), eq(NotificationStatus.FAILED), eq(NotificationStatus.PROCESSING), any(Instant.class)))
                .thenReturn(1);

        assertThat(statusService.claimRetry(id)).isTrue();
        verify(notificationRepository).claimRetryIfCurrent(
                eq(id), eq(NotificationStatus.FAILED), eq(NotificationStatus.PROCESSING), any(Instant.class));
    }

    @Test
    void markDeadTransitionsFailedToDead() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.updateStatusIfCurrent(
                        eq(id), eq(NotificationStatus.FAILED), eq(NotificationStatus.DEAD), any(Instant.class)))
                .thenReturn(1);

        assertThat(statusService.markDead(id)).isTrue();
    }

    @Test
    void truncateLastErrorTo1024() {
        UUID id = UUID.randomUUID();
        String tooLong = "x".repeat(2000);
        when(notificationRepository.markFailedIfCurrent(
                        eq(id),
                        eq(NotificationStatus.PROCESSING),
                        eq(NotificationStatus.FAILED),
                        any(String.class),
                        any(Instant.class)))
                .thenReturn(1);

        statusService.markFailed(id, tooLong);

        verify(notificationRepository).markFailedIfCurrent(
                eq(id),
                eq(NotificationStatus.PROCESSING),
                eq(NotificationStatus.FAILED),
                eq("x".repeat(1024)),
                any(Instant.class));
    }

    @Test
    void markSentWritesProviderMessageId() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.markSentIfCurrent(
                        eq(id),
                        eq(NotificationStatus.PROCESSING),
                        eq(NotificationStatus.SENT),
                        any(Instant.class),
                        eq("SM123"),
                        any(Instant.class)))
                .thenReturn(1);

        assertThat(statusService.markSent(id, "SM123")).isTrue();
    }

    @Test
    void markSentTruncatesProviderMessageId() {
        UUID id = UUID.randomUUID();
        String tooLong = "p".repeat(300);
        when(notificationRepository.markSentIfCurrent(
                        eq(id),
                        eq(NotificationStatus.PROCESSING),
                        eq(NotificationStatus.SENT),
                        any(Instant.class),
                        any(String.class),
                        any(Instant.class)))
                .thenReturn(1);

        statusService.markSent(id, tooLong);

        verify(notificationRepository).markSentIfCurrent(
                eq(id),
                eq(NotificationStatus.PROCESSING),
                eq(NotificationStatus.SENT),
                any(Instant.class),
                eq("p".repeat(255)),
                any(Instant.class));
    }
}
