package com.smartshift.service.impl;

import com.smartshift.config.LarkProperties;
import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.lark.LarkDeliveryResponse;
import com.smartshift.dto.lark.LarkIntegrationStatusResponse;
import com.smartshift.entity.LarkDeliveryLog;
import com.smartshift.entity.User;
import com.smartshift.enums.LarkDeliveryChannel;
import com.smartshift.enums.LarkDeliveryStatus;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.integration.lark.LarkAppClient;
import com.smartshift.integration.lark.LarkClientResult;
import com.smartshift.integration.lark.LarkWebhookClient;
import com.smartshift.repository.LarkDeliveryLogRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.LarkIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LarkIntegrationServiceImpl implements LarkIntegrationService {

    private static final int DELIVERY_BATCH_SIZE = 50;
    private static final Set<NotificationType> GROUP_TYPES = EnumSet.of(
        NotificationType.SCHEDULE_PUBLISHED,
        NotificationType.TIME_OFF_REQUEST_CREATED,
        NotificationType.OPEN_SHIFT_CLAIM_CREATED,
        NotificationType.SHIFT_SWAP_REQUEST_ACCEPTED
    );

    private final LarkDeliveryLogRepository deliveryRepository;
    private final UserRepository userRepository;
    private final LarkWebhookClient larkWebhookClient;
    private final LarkAppClient larkAppClient;
    private final LarkProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void enqueueNotification(
        User recipient,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    ) {
        if (properties.appEnabled()) {
            enqueuePersonal(
                recipient,
                type.name(),
                title,
                appendSmartShiftLink(type, content),
                referenceType,
                referenceId,
                notificationEventKey(
                    LarkDeliveryChannel.PERSONAL_APP,
                    recipient.getId(),
                    type,
                    title,
                    content,
                    referenceType,
                    referenceId
                )
            );
        }
        if (properties.enabled() && GROUP_TYPES.contains(type)) {
            enqueueGroup(
                type.name(),
                title,
                appendSmartShiftLink(type, content),
                referenceType,
                referenceId,
                notificationEventKey(
                    LarkDeliveryChannel.GROUP_WEBHOOK,
                    null,
                    type,
                    title,
                    content,
                    referenceType,
                    referenceId
                )
            );
        }
    }

    @Override
    public LarkIntegrationStatusResponse getStatus() {
        LarkDeliveryLog latestFailure = deliveryRepository
            .findFirstByStatusOrderByUpdatedAtDesc(LarkDeliveryStatus.FAILED)
            .orElse(null);
        Instant lastSentAt = deliveryRepository
            .findFirstByStatusOrderBySentAtDesc(LarkDeliveryStatus.SENT)
            .map(LarkDeliveryLog::getSentAt)
            .orElse(null);
        return new LarkIntegrationStatusResponse(
            properties.enabled(),
            properties.configured(),
            properties.groupReady(),
            properties.signatureEnabled(),
            properties.appEnabled(),
            properties.appConfigured(),
            properties.personalReady(),
            maxAttempts(),
            userRepository.countByActiveTrue(),
            userRepository.countByActiveTrueAndLarkOpenIdIsNotNull(),
            userRepository.countByActiveTrueAndLarkSyncErrorIsNotNull(),
            deliveryRepository.countByStatus(LarkDeliveryStatus.PENDING),
            deliveryRepository.countByStatus(LarkDeliveryStatus.SENT),
            deliveryRepository.countByStatus(LarkDeliveryStatus.FAILED),
            lastSentAt,
            latestFailure == null ? null : latestFailure.getLastError(),
            clock.instant()
        );
    }

    @Override
    public PageResponse<LarkDeliveryResponse> getDeliveries(
        LarkDeliveryStatus status,
        int page,
        int size
    ) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<LarkDeliveryLog> result = status == null
            ? deliveryRepository.findAllByOrderByCreatedAtDesc(pageable)
            : deliveryRepository.findByStatusOrderByCreatedAtDesc(
                status,
                pageable
            );
        return PageResponse.from(result.map(this::toResponse));
    }

    @Override
    @Transactional
    public LarkDeliveryResponse enqueueGroupTest(String requestedBy) {
        if (!properties.groupReady()) {
            throw new BusinessRuleException(
                "Webhook nhóm Lark chưa được bật hoặc chưa được cấu hình"
            );
        }
        String eventKey = "test-group:" + UUID.randomUUID();
        enqueueGroup(
            "GROUP_CONNECTION_TEST",
            "Kiểm tra kênh nhóm Lark",
            "Tin nhắn thử được gửi từ trang quản trị bởi " + requestedBy
                + ".\nMở Smart Shift: " + smartShiftBaseUrl(),
            null,
            null,
            eventKey
        );
        return findByEventKey(eventKey);
    }

    @Override
    @Transactional
    public LarkDeliveryResponse enqueuePersonalTest(Long recipientUserId) {
        if (!properties.personalReady()) {
            throw new BusinessRuleException(
                "Lark App Bot chưa được bật hoặc chưa được cấu hình"
            );
        }
        User recipient = userRepository.findById(recipientUserId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy nhân viên có id " + recipientUserId
            ));
        if (recipient.getLarkOpenId() == null) {
            throw new BusinessRuleException(
                "Nhân viên chưa liên kết tài khoản Lark"
            );
        }
        String eventKey = "test-personal:" + UUID.randomUUID();
        enqueuePersonal(
            recipient,
            "PERSONAL_CONNECTION_TEST",
            "Kiểm tra tin nhắn cá nhân",
            "Tài khoản của bạn đã được liên kết với Smart Shift."
                + "\nMở Smart Shift: " + smartShiftBaseUrl(),
            null,
            null,
            eventKey
        );
        return findByEventKey(eventKey);
    }

    @Override
    @Transactional
    public LarkDeliveryResponse retry(Long id) {
        LarkDeliveryLog delivery = deliveryRepository.findByIdForUpdate(id)
            .orElseThrow(() -> deliveryNotFound(id));
        if (delivery.getStatus() != LarkDeliveryStatus.FAILED) {
            throw new BusinessRuleException(
                "Chỉ có thể gửi lại thông báo Lark đang ở trạng thái thất bại"
            );
        }

        if (delivery.getChannel() == LarkDeliveryChannel.GROUP_WEBHOOK) {
            if (!properties.groupReady()) {
                throw new BusinessRuleException(
                    "Webhook nhóm Lark chưa sẵn sàng"
                );
            }
        } else {
            if (!properties.personalReady()) {
                throw new BusinessRuleException("Lark App Bot chưa sẵn sàng");
            }
            User recipient = findRecipient(delivery);
            if (recipient.getLarkOpenId() == null) {
                throw new BusinessRuleException(
                    "Nhân viên chưa liên kết tài khoản Lark"
                );
            }
            delivery.setRecipientOpenId(recipient.getLarkOpenId());
            delivery.setRecipientName(recipient.getFullName());
        }

        resetForRetry(delivery);
        return toResponse(deliveryRepository.save(delivery));
    }

    @Override
    @Transactional
    public void processPending() {
        if (!properties.groupReady() && !properties.personalReady()) {
            return;
        }
        Instant now = clock.instant();
        deliveryRepository.findPendingIds(
            now,
            PageRequest.of(0, DELIVERY_BATCH_SIZE)
        ).forEach(id -> processOne(id, now));
    }

    private void processOne(Long id, Instant startedAt) {
        LarkDeliveryLog delivery = deliveryRepository.findByIdForUpdate(id)
            .orElse(null);
        if (delivery == null
            || delivery.getStatus() != LarkDeliveryStatus.PENDING
            || delivery.getNextAttemptAt() == null
            || delivery.getNextAttemptAt().isAfter(startedAt)) {
            return;
        }

        LarkClientResult result;
        if (delivery.getChannel() == LarkDeliveryChannel.GROUP_WEBHOOK) {
            if (!properties.groupReady()) {
                return;
            }
            result = larkWebhookClient.send(
                delivery.getTitle(),
                delivery.getContent()
            );
        } else {
            if (!properties.personalReady()) {
                return;
            }
            User recipient = findRecipientOrNull(delivery);
            if (recipient == null) {
                markRecipientUnavailable(delivery);
                deliveryRepository.save(delivery);
                return;
            }
            if (recipient.getLarkOpenId() == null) {
                markRecipientUnavailable(delivery);
                deliveryRepository.save(delivery);
                return;
            }
            delivery.setRecipientOpenId(recipient.getLarkOpenId());
            delivery.setRecipientName(recipient.getFullName());
            result = larkAppClient.sendMessage(
                recipient.getLarkOpenId(),
                delivery.getTitle(),
                delivery.getContent()
            );
        }

        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastAttemptAt(startedAt);
        delivery.setHttpStatus(result.httpStatus());
        delivery.setResponseBody(result.responseBody());
        applyResult(delivery, result);
        deliveryRepository.save(delivery);
    }

    private void applyResult(
        LarkDeliveryLog delivery,
        LarkClientResult result
    ) {
        if (result.success()) {
            delivery.setStatus(LarkDeliveryStatus.SENT);
            delivery.setSentAt(clock.instant());
            delivery.setNextAttemptAt(null);
            delivery.setLastError(null);
            return;
        }

        delivery.setLastError(result.error());
        if (delivery.getAttemptCount() >= maxAttempts()) {
            delivery.setStatus(LarkDeliveryStatus.FAILED);
            delivery.setNextAttemptAt(null);
        } else {
            delivery.setNextAttemptAt(
                clock.instant().plus(properties.retryDelay())
            );
        }
    }

    private void enqueueGroup(
        String notificationType,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId,
        String eventKey
    ) {
        enqueue(
            eventKey,
            notificationType,
            LarkDeliveryChannel.GROUP_WEBHOOK,
            null,
            null,
            null,
            title,
            content,
            referenceType,
            referenceId,
            properties.configured(),
            "Chưa cấu hình LARK_WEBHOOK_URL"
        );
    }

    private void enqueuePersonal(
        User recipient,
        String notificationType,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId,
        String eventKey
    ) {
        boolean ready = properties.appConfigured()
            && recipient.getLarkOpenId() != null;
        String initialError = properties.appConfigured()
            ? "Nhân viên chưa liên kết tài khoản Lark"
            : "Chưa cấu hình LARK_APP_ID/LARK_APP_SECRET";
        enqueue(
            eventKey,
            notificationType,
            LarkDeliveryChannel.PERSONAL_APP,
            recipient.getId(),
            recipient.getFullName(),
            recipient.getLarkOpenId(),
            title,
            content,
            referenceType,
            referenceId,
            ready,
            initialError
        );
    }

    private void enqueue(
        String eventKey,
        String notificationType,
        LarkDeliveryChannel channel,
        Long recipientUserId,
        String recipientName,
        String recipientOpenId,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId,
        boolean ready,
        String initialError
    ) {
        deliveryRepository.enqueueIfAbsent(
            eventKey,
            notificationType,
            channel.name(),
            recipientUserId,
            recipientName,
            recipientOpenId,
            referenceType == null ? null : referenceType.name(),
            referenceId,
            title,
            content,
            ready
                ? LarkDeliveryStatus.PENDING.name()
                : LarkDeliveryStatus.FAILED.name(),
            ready ? clock.instant() : null,
            ready ? null : initialError
        );
    }

    private String notificationEventKey(
        LarkDeliveryChannel channel,
        Long recipientUserId,
        NotificationType type,
        String title,
        String content,
        NotificationReferenceType referenceType,
        Long referenceId
    ) {
        String value = channel.name()
            + '|' + recipientUserId
            + '|' + type.name()
            + '|' + referenceType
            + '|' + referenceId
            + '|' + title
            + '|' + content;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return "notification:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "Máy chủ không hỗ trợ SHA-256",
                exception
            );
        }
    }

    private String appendSmartShiftLink(
        NotificationType type,
        String content
    ) {
        return content + "\nMở Smart Shift: "
            + smartShiftBaseUrl() + targetPath(type);
    }

    private String smartShiftBaseUrl() {
        String value = properties.smartShiftUrl();
        if (value == null || value.isBlank()) {
            return "http://localhost:3000";
        }
        String normalized = value.trim();
        return normalized.endsWith("/")
            ? normalized.substring(0, normalized.length() - 1)
            : normalized;
    }

    private String targetPath(NotificationType type) {
        return switch (type) {
            case SCHEDULE_PUBLISHED -> "/my-schedule";
            case TIME_OFF_REQUEST_CREATED -> "/time-off/review";
            case TIME_OFF_REQUEST_APPROVED,
                 TIME_OFF_REQUEST_REJECTED -> "/time-off";
            case OPEN_SHIFT_CLAIM_CREATED -> "/open-shifts/review";
            case OPEN_SHIFT_CLAIM_APPROVED,
                 OPEN_SHIFT_CLAIM_REJECTED -> "/open-shifts";
            case SHIFT_SWAP_REQUEST_ACCEPTED -> "/shift-swaps/review";
            case SHIFT_SWAP_REQUEST_CREATED,
                 SHIFT_SWAP_REQUEST_DECLINED,
                 SHIFT_SWAP_REQUEST_APPROVED,
                 SHIFT_SWAP_REQUEST_REJECTED,
                 SHIFT_SWAP_REQUEST_CANCELLED -> "/shift-swaps";
            case PAYROLL_CONFIRMED -> "/payroll";
        };
    }

    private User findRecipient(LarkDeliveryLog delivery) {
        if (delivery.getRecipientUserId() == null) {
            throw new BusinessRuleException(
                "Lần gửi cá nhân không còn thông tin người nhận"
            );
        }
        return userRepository.findById(delivery.getRecipientUserId())
            .orElseThrow(() -> new BusinessRuleException(
                "Người nhận thông báo Lark không còn tồn tại"
            ));
    }

    private User findRecipientOrNull(LarkDeliveryLog delivery) {
        if (delivery.getRecipientUserId() == null) {
            return null;
        }
        return userRepository.findById(delivery.getRecipientUserId())
            .orElse(null);
    }

    private void markRecipientUnavailable(LarkDeliveryLog delivery) {
        delivery.setStatus(LarkDeliveryStatus.FAILED);
        delivery.setNextAttemptAt(null);
        delivery.setLastAttemptAt(clock.instant());
        delivery.setLastError(
            delivery.getRecipientUserId() == null
                ? "Lần gửi cá nhân không còn thông tin người nhận"
                : "Nhân viên không tồn tại hoặc chưa liên kết tài khoản Lark"
        );
    }

    private void resetForRetry(LarkDeliveryLog delivery) {
        delivery.setStatus(LarkDeliveryStatus.PENDING);
        delivery.setAttemptCount(0);
        delivery.setLastAttemptAt(null);
        delivery.setNextAttemptAt(clock.instant());
        delivery.setSentAt(null);
        delivery.setLastError(null);
        delivery.setHttpStatus(null);
        delivery.setResponseBody(null);
    }

    private int maxAttempts() {
        return Math.max(1, properties.maxAttempts());
    }

    private LarkDeliveryResponse findByEventKey(String eventKey) {
        return deliveryRepository.findByEventKey(eventKey)
            .map(this::toResponse)
            .orElseThrow(() -> new IllegalStateException(
                "Không thể tạo lần gửi thử Lark"
            ));
    }

    private LarkDeliveryResponse toResponse(LarkDeliveryLog delivery) {
        return new LarkDeliveryResponse(
            delivery.getId(),
            delivery.getNotificationType(),
            delivery.getChannel(),
            delivery.getRecipientUserId(),
            delivery.getRecipientName(),
            delivery.getReferenceType(),
            delivery.getReferenceId(),
            delivery.getTitle(),
            delivery.getContent(),
            delivery.getStatus(),
            delivery.getAttemptCount(),
            delivery.getLastAttemptAt(),
            delivery.getNextAttemptAt(),
            delivery.getSentAt(),
            delivery.getLastError(),
            delivery.getHttpStatus(),
            delivery.getCreatedAt(),
            delivery.getUpdatedAt()
        );
    }

    private ResourceNotFoundException deliveryNotFound(Long id) {
        return new ResourceNotFoundException(
            "Không tìm thấy lần gửi Lark có id " + id
        );
    }
}
