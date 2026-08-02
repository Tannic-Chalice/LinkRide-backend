package com.linkride.backend.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * The inbox query: unread-first, then newest-first. Deliberately a custom {@code @Query}, not
     * a derived {@code OrderByStatusAscCreatedAtDesc} — {@code status} is stored as text
     * ({@code ARCHIVED, READ, UNREAD} alphabetically), so a plain column-order sort would put
     * {@code UNREAD} last, the opposite of what an inbox needs.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.recipientUserId = :recipientUserId
              AND (:category IS NULL OR n.category = :category)
              AND (:status IS NULL OR n.status = :status)
            ORDER BY CASE WHEN n.status = com.linkride.backend.notification.NotificationStatus.UNREAD THEN 0 ELSE 1 END,
                     n.createdAt DESC
            """)
    Page<Notification> findInbox(
            @Param("recipientUserId") UUID recipientUserId,
            @Param("category") NotificationCategory category,
            @Param("status") NotificationStatus status,
            Pageable pageable);

    long countByRecipientUserIdAndStatusAndCategory(UUID recipientUserId, NotificationStatus status, NotificationCategory category);

    long countByRecipientUserIdAndStatus(UUID recipientUserId, NotificationStatus status);

    /** Devtools only: a recent, all-recipients feed for {@code GET /api/v1/devtools/push-log} — manual
     * end-to-end verification of the event -> notification pipeline without a mobile client. */
    List<Notification> findTop50ByOrderByCreatedAtDesc();

    Optional<Notification> findByNotificationIdAndRecipientUserId(UUID notificationId, UUID recipientUserId);

    @Modifying
    @Query("""
            UPDATE Notification n SET n.status = com.linkride.backend.notification.NotificationStatus.READ,
                   n.readAt = CURRENT_TIMESTAMP
            WHERE n.recipientUserId = :recipientUserId
              AND n.status = com.linkride.backend.notification.NotificationStatus.UNREAD
              AND (:category IS NULL OR n.category = :category)
            """)
    int markAllRead(@Param("recipientUserId") UUID recipientUserId, @Param("category") NotificationCategory category);
}
