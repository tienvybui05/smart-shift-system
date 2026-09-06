package com.smartshift.service.impl;

import com.smartshift.entity.Notification;
import com.smartshift.entity.User;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.NotificationMapper;
import com.smartshift.repository.NotificationRepository;
import com.smartshift.service.LarkIntegrationService;
import com.smartshift.service.NotificationStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    private static final Instant NOW = Instant.parse(
        "2026-08-28T10:00:00Z"
    );

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationStreamService notificationStreamService;

    @Mock
    private LarkIntegrationService larkIntegrationService;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(
            notificationRepository,
            new NotificationMapper(),
            notificationStreamService,
            larkIntegrationService,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void getMyNotificationsUsesAuthenticatedUsernameAndUnreadFilter() {
        Notification notification = notification(1L, user(2L, "employee"));
        PageRequest pageable = PageRequest.of(0, 20);
        when(notificationRepository.searchByUser(
            "employee",
            true,
            pageable
        )).thenReturn(new PageImpl<>(List.of(notification), pageable, 1));

        var response = service.getMyNotifications(
            "employee",
            true,
            0,
            20
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content().get(0).id()).isEqualTo(1L);
    }

    @Test
    void markAsReadOnlyFindsNotificationOwnedByCurrentUser() {
        Notification notification = notification(1L, user(2L, "employee"));
        when(notificationRepository.findByIdAndUserUsername(1L, "employee"))
            .thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification))
            .thenReturn(notification);

        var response = service.markAsRead(1L, "employee");

        assertThat(response.readAt()).isEqualTo(NOW);
        verify(notificationRepository).findByIdAndUserUsername(
            1L,
            "employee"
        );
    }

    @Test
    void markAsReadHidesNotificationOwnedByAnotherUser() {
        when(notificationRepository.findByIdAndUserUsername(1L, "employee"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(1L, "employee"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createNotificationPersistsAndPushesToConnectedUser() {
        User recipient = user(2L, "employee");
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> {
                Notification saved = invocation.getArgument(0);
                saved.setId(3L);
                saved.setCreatedAt(NOW);
                return saved;
            });

        var response = service.createNotification(
            recipient,
            NotificationType.SCHEDULE_PUBLISHED,
            "Lịch mới",
            "Lịch tuần đã được công bố.",
            NotificationReferenceType.SCHEDULE_PERIOD,
            10L
        );

        assertThat(response.id()).isEqualTo(3L);
        verify(notificationStreamService).send("employee", response);
    }

    private Notification notification(Long id, User recipient) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUser(recipient);
        notification.setType(NotificationType.SCHEDULE_PUBLISHED);
        notification.setTitle("Lịch mới");
        notification.setContent("Lịch tuần đã được công bố.");
        notification.setReferenceType(
            NotificationReferenceType.SCHEDULE_PERIOD
        );
        notification.setReferenceId(10L);
        notification.setCreatedAt(NOW.minusSeconds(60));
        return notification;
    }

    private User user(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setFullName(username);
        return user;
    }
}
