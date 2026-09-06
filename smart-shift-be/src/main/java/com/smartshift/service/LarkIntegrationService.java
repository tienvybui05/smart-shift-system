package com.smartshift.service;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.lark.LarkDeliveryResponse;
import com.smartshift.dto.lark.LarkIntegrationStatusResponse;
import com.smartshift.entity.User;
import com.smartshift.enums.LarkDeliveryStatus;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;

public interface LarkIntegrationService {

    void enqueueNotification(
        User recipient,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    );

    LarkIntegrationStatusResponse getStatus();

    PageResponse<LarkDeliveryResponse> getDeliveries(
        LarkDeliveryStatus status,
        int page,
        int size
    );

    LarkDeliveryResponse enqueueGroupTest(String requestedBy);

    LarkDeliveryResponse enqueuePersonalTest(Long recipientUserId);

    LarkDeliveryResponse retry(Long id);

    void processPending();
}
