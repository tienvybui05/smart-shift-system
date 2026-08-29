package com.smartshift.controller;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.notification.MarkAllNotificationsReadResponse;
import com.smartshift.dto.notification.NotificationCountResponse;
import com.smartshift.dto.notification.NotificationResponse;
import com.smartshift.service.NotificationService;
import com.smartshift.service.NotificationStreamService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreamService notificationStreamService;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> getMine(
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "0")
        @Min(value = 0, message = "Trang thông báo không được âm")
        int page,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "Số thông báo mỗi trang phải lớn hơn 0")
        @Max(value = 100, message = "Chỉ được lấy tối đa 100 thông báo mỗi trang")
        int size,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            notificationService.getMyNotifications(
                authentication.getName(),
                unreadOnly,
                page,
                size
            )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationCountResponse> getUnreadCount(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            new NotificationCountResponse(
                notificationService.getMyUnreadCount(
                    authentication.getName()
                )
            )
        );
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        return notificationStreamService.subscribe(authentication.getName());
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markAsRead(
        @PathVariable
        @Positive(message = "Id thông báo phải lớn hơn 0")
        Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            notificationService.markAsRead(
                id,
                authentication.getName()
            )
        );
    }

    @PatchMapping("/read-all")
    public ResponseEntity<MarkAllNotificationsReadResponse> markAllAsRead(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            new MarkAllNotificationsReadResponse(
                notificationService.markAllAsRead(
                    authentication.getName()
                )
            )
        );
    }
}
