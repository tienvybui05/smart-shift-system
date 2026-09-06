package com.smartshift.dto.lark;

import com.smartshift.enums.LarkDeliveryStatus;
import com.smartshift.enums.LarkDeliveryChannel;
import com.smartshift.enums.NotificationReferenceType;

import java.time.Instant;

public record LarkDeliveryResponse(
    Long id,
    String notificationType,
    LarkDeliveryChannel channel,
    Long recipientUserId,
    String recipientName,
    NotificationReferenceType referenceType,
    Long referenceId,
    String title,
    String content,
    LarkDeliveryStatus status,
    int attemptCount,
    Instant lastAttemptAt,
    Instant nextAttemptAt,
    Instant sentAt,
    String lastError,
    Integer httpStatus,
    Instant createdAt,
    Instant updatedAt
) {
}
