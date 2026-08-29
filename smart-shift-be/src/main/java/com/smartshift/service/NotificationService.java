package com.smartshift.service;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.notification.NotificationResponse;
import com.smartshift.entity.User;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;

import java.util.Collection;

public interface NotificationService {

    PageResponse<NotificationResponse> getMyNotifications(
        String username,
        boolean unreadOnly,
        int page,
        int size
    );

    long getMyUnreadCount(String username);

    NotificationResponse markAsRead(Long id, String username);

    int markAllAsRead(String username);

    NotificationResponse createNotification(
        User recipient,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    );

    void createNotifications(
        Collection<User> recipients,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    );
}
