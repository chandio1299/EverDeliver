package com.everdeliver.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    List<Notification> findByStatusAndUpdatedAtBefore(NotificationStatus status, Instant cutoff);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = :queued, n.updatedAt = :now
            where n.status = :processing and n.updatedAt < :cutoff
            """)
    int requeueStaleProcessing(
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now,
            @Param("queued") NotificationStatus queued,
            @Param("processing") NotificationStatus processing);

    default int requeueStaleProcessing(Instant cutoff, Instant now) {
        return requeueStaleProcessing(
                cutoff, now, NotificationStatus.QUEUED, NotificationStatus.PROCESSING);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = :queued, n.updatedAt = :now
            where n.id = :id and n.status = :processing and n.updatedAt < :cutoff
            """)
    int requeueStaleProcessingById(
            @Param("id") UUID id,
            @Param("cutoff") Instant cutoff,
            @Param("now") Instant now,
            @Param("queued") NotificationStatus queued,
            @Param("processing") NotificationStatus processing);

    default int requeueStaleProcessingById(UUID id, Instant cutoff, Instant now) {
        return requeueStaleProcessingById(
                id, cutoff, now, NotificationStatus.QUEUED, NotificationStatus.PROCESSING);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = :toStatus, n.updatedAt = :now
            where n.id = :id and n.status = :fromStatus
            """)
    int updateStatusIfCurrent(
            @Param("id") UUID id,
            @Param("fromStatus") NotificationStatus fromStatus,
            @Param("toStatus") NotificationStatus toStatus,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = :toStatus, n.sentAt = :sentAt, n.providerMessageId = :providerMessageId, n.updatedAt = :now
            where n.id = :id and n.status = :fromStatus
            """)
    int markSentIfCurrent(
            @Param("id") UUID id,
            @Param("fromStatus") NotificationStatus fromStatus,
            @Param("toStatus") NotificationStatus toStatus,
            @Param("sentAt") Instant sentAt,
            @Param("providerMessageId") String providerMessageId,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = :toStatus, n.lastError = :lastError, n.updatedAt = :now
            where n.id = :id and n.status = :fromStatus
            """)
    int markFailedIfCurrent(
            @Param("id") UUID id,
            @Param("fromStatus") NotificationStatus fromStatus,
            @Param("toStatus") NotificationStatus toStatus,
            @Param("lastError") String lastError,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = :toStatus, n.retryCount = n.retryCount + 1, n.updatedAt = :now
            where n.id = :id and n.status = :fromStatus
            """)
    int claimRetryIfCurrent(
            @Param("id") UUID id,
            @Param("fromStatus") NotificationStatus fromStatus,
            @Param("toStatus") NotificationStatus toStatus,
            @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.status = :queued, n.lastError = null, n.updatedAt = :now
            where n.id = :id and n.status in (:failed, :dead)
            """)
    int claimManualRetry(
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("queued") NotificationStatus queued,
            @Param("failed") NotificationStatus failed,
            @Param("dead") NotificationStatus dead);

    default int claimManualRetry(UUID id, Instant now) {
        return claimManualRetry(
                id, now, NotificationStatus.QUEUED, NotificationStatus.FAILED, NotificationStatus.DEAD);
    }

    @Query("""
            select n.status, count(n)
            from Notification n
            where (:hasSince = false or n.createdAt >= :since)
            group by n.status
            """)
    List<Object[]> countGroupedByStatus(@Param("hasSince") boolean hasSince, @Param("since") Instant since);

    default List<Object[]> countGroupedByStatus(Instant since) {
        return countGroupedByStatus(since != null, since);
    }

    @Query("""
            select n.channel, count(n)
            from Notification n
            where (:hasSince = false or n.createdAt >= :since)
            group by n.channel
            """)
    List<Object[]> countGroupedByChannel(@Param("hasSince") boolean hasSince, @Param("since") Instant since);

    default List<Object[]> countGroupedByChannel(Instant since) {
        return countGroupedByChannel(since != null, since);
    }

    @Query("""
            select n.channel, count(n)
            from Notification n
            where n.status in (:failed, :dead)
              and (:hasSince = false or n.createdAt >= :since)
            group by n.channel
            """)
    List<Object[]> countFailuresGroupedByChannel(
            @Param("hasSince") boolean hasSince,
            @Param("since") Instant since,
            @Param("failed") NotificationStatus failed,
            @Param("dead") NotificationStatus dead);

    default List<Object[]> countFailuresGroupedByChannel(Instant since) {
        return countFailuresGroupedByChannel(
                since != null, since, NotificationStatus.FAILED, NotificationStatus.DEAD);
    }

    @Query(
            value = """
                    SELECT AVG(EXTRACT(EPOCH FROM (sent_at - created_at)) * 1000)
                    FROM notifications
                    WHERE status = 'SENT'
                      AND sent_at IS NOT NULL
                      AND (CAST(:hasSince AS boolean) = false OR created_at >= CAST(:since AS timestamptz))
                    """,
            nativeQuery = true)
    Double averageSentLatencyMs(@Param("hasSince") boolean hasSince, @Param("since") Instant since);
}
