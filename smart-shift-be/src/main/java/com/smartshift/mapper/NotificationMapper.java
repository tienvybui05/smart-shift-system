package com.smartshift.mapper;

import com.smartshift.dto.notification.NotificationResponse;
import com.smartshift.entity.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getContent(),
            notification.getReferenceType(),
            notification.getReferenceId(),
            notification.getReadAt(),
            notification.getCreatedAt()
        );
    }
}
