package com.smartshift.repository;

import com.smartshift.entity.LarkDeliveryLog;
import com.smartshift.enums.LarkDeliveryStatus;
import com.smartshift.enums.LarkDeliveryChannel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LarkDeliveryLogRepository
    extends JpaRepository<LarkDeliveryLog, Long> {

    Optional<LarkDeliveryLog> findByEventKey(String eventKey);

    Page<LarkDeliveryLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LarkDeliveryLog> findByStatusOrderByCreatedAtDesc(
        LarkDeliveryStatus status,
        Pageable pageable
    );

    long countByStatus(LarkDeliveryStatus status);

    long countByChannelAndStatus(
        LarkDeliveryChannel channel,
        LarkDeliveryStatus status
    );

    Optional<LarkDeliveryLog> findFirstByStatusOrderBySentAtDesc(
        LarkDeliveryStatus status
    );

    Optional<LarkDeliveryLog> findFirstByStatusOrderByUpdatedAtDesc(
        LarkDeliveryStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT delivery FROM LarkDeliveryLog delivery WHERE delivery.id = :id")
    Optional<LarkDeliveryLog> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT delivery.id
        FROM LarkDeliveryLog delivery
        WHERE delivery.status = com.smartshift.enums.LarkDeliveryStatus.PENDING
          AND delivery.nextAttemptAt <= :now
        ORDER BY delivery.createdAt ASC, delivery.id ASC
        """)
    List<Long> findPendingIds(
        @Param("now") Instant now,
        Pageable pageable
    );

    @Modifying
    @Query(value = """
        INSERT INTO lark_delivery_logs (
            event_key,
            notification_type,
            channel,
            recipient_user_id,
            recipient_name,
            recipient_open_id,
            reference_type,
            reference_id,
            title,
            content,
            status,
            attempt_count,
            next_attempt_at,
            last_error,
            created_at,
            updated_at
        ) VALUES (
            :eventKey,
            :notificationType,
            :channel,
            :recipientUserId,
            :recipientName,
            :recipientOpenId,
            :referenceType,
            :referenceId,
            :title,
            :content,
            :status,
            0,
            :nextAttemptAt,
            :lastError,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (event_key) DO NOTHING
        """, nativeQuery = true)
    int enqueueIfAbsent(
        @Param("eventKey") String eventKey,
        @Param("notificationType") String notificationType,
        @Param("channel") String channel,
        @Param("recipientUserId") Long recipientUserId,
        @Param("recipientName") String recipientName,
        @Param("recipientOpenId") String recipientOpenId,
        @Param("referenceType") String referenceType,
        @Param("referenceId") Long referenceId,
        @Param("title") String title,
        @Param("content") String content,
        @Param("status") String status,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("lastError") String lastError
    );

    @Modifying
    @Query(value = """
        UPDATE lark_delivery_logs
        SET status = 'PENDING',
            attempt_count = 0,
            last_attempt_at = NULL,
            next_attempt_at = CURRENT_TIMESTAMP,
            last_error = NULL,
            http_status = NULL,
            response_body = NULL,
            recipient_open_id = :openId,
            updated_at = CURRENT_TIMESTAMP
        WHERE recipient_user_id = :userId
          AND channel = 'PERSONAL_APP'
          AND status = 'FAILED'
        """, nativeQuery = true)
    int reactivateFailedPersonalDeliveries(
        @Param("userId") Long userId,
        @Param("openId") String openId
    );
}
