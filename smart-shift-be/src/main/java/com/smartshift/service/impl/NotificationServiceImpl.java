package com.smartshift.service.impl;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.notification.NotificationResponse;
import com.smartshift.entity.Notification;
import com.smartshift.entity.User;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.NotificationMapper;
import com.smartshift.repository.NotificationRepository;
import com.smartshift.service.NotificationService;
import com.smartshift.service.NotificationStreamService;
import com.smartshift.service.LarkIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationStreamService notificationStreamService;
    private final LarkIntegrationService larkIntegrationService;
    private final Clock clock;

    @Override
    public PageResponse<NotificationResponse> getMyNotifications(
        String username,
        boolean unreadOnly,
        int page,
        int size
    ) {
        Page<NotificationResponse> result = notificationRepository
            .searchByUser(
                username,
                unreadOnly,
                PageRequest.of(page, size)
            )
            .map(notificationMapper::toResponse);
        return PageResponse.from(result);
    }

    @Override
    public long getMyUnreadCount(String username) {
        return notificationRepository
            .countByUserUsernameAndReadAtIsNull(username);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long id, String username) {
        Notification notification = notificationRepository
            .findByIdAndUserUsername(id, username)
            .orElseThrow(() -> notificationNotFound(id));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
        }
        return notificationMapper.toResponse(
            notificationRepository.save(notification)
        );
    }

    @Override
    @Transactional
    public int markAllAsRead(String username) {
        return notificationRepository.markAllAsRead(
            username,
            clock.instant()
        );
    }

    @Override
    @Transactional
    public NotificationResponse createNotification(
        User recipient,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    ) {
        validateReference(referenceType, referenceId);
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);

        NotificationResponse response = notificationMapper.toResponse(
            notificationRepository.save(notification)
        );
        enqueueLarkAfterCommit(
            recipient,
            type,
            title,
            content,
            referenceType,
            referenceId
        );
        publishAfterCommit(recipient.getUsername(), response);
        return response;
    }

    private void enqueueLarkAfterCommit(
        User recipient,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    ) {
        Runnable enqueue = () -> {
            try {
                larkIntegrationService.enqueueNotification(
                    recipient,
                    type,
                    title,
                    content,
                    referenceType,
                    referenceId
                );
            } catch (RuntimeException exception) {
                log.warn(
                    "Unable to enqueue Lark notification for user {}",
                    recipient.getId(),
                    exception
                );
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            enqueue.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue.run();
                }
            }
        );
    }

    @Override
    @Transactional
    public void createNotifications(
        Collection<User> recipients,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    ) {
        LinkedHashMap<Long, User> uniqueRecipients = new LinkedHashMap<>();
        for (User recipient : recipients) {
            uniqueRecipients.putIfAbsent(recipient.getId(), recipient);
        }
        for (User recipient : uniqueRecipients.values()) {
            createNotification(
                recipient,
                type,
                title,
                content,
                referenceType,
                referenceId
            );
        }
    }

    private void publishAfterCommit(
        String username,
        NotificationResponse response
    ) {
        if (!TransactionSynchronizationManager
            .isSynchronizationActive()) {
            notificationStreamService.send(username, response);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notificationStreamService.send(username, response);
                }
            }
        );
    }

    private void validateReference(
        NotificationReferenceType referenceType,
        Long referenceId
    ) {
        if ((referenceType == null) != (referenceId == null)) {
            throw new IllegalArgumentException(
                "Loại và id tham chiếu thông báo phải cùng có hoặc cùng trống"
            );
        }
    }

    private ResourceNotFoundException notificationNotFound(Long id) {
        return new ResourceNotFoundException(
            "Không tìm thấy thông báo có id " + id
        );
    }
}
