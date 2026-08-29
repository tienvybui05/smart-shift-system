package com.smartshift.dto.notification;

import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
    Long id,
    NotificationType type,
    String title,
    String content,
    NotificationReferenceType referenceType,
    Long referenceId,
    Instant readAt,
    Instant createdAt
) {
}
