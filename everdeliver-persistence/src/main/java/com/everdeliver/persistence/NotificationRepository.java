package com.everdeliver.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

    List<Notification> findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(Instant since, Pageable pageable);

    List<Notification> findByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            NotificationStatus status, Instant since, Pageable pageable);

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
}
