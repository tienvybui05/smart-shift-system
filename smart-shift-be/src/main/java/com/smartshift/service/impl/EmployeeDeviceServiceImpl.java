package com.smartshift.service.impl;

import com.smartshift.dto.device.DeviceCheckInRequest;
import com.smartshift.dto.device.DeviceReviewRequest;
import com.smartshift.dto.device.EmployeeDeviceResponse;
import com.smartshift.entity.EmployeeDevice;
import com.smartshift.entity.User;
import com.smartshift.enums.DeviceStatus;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.EmployeeDeviceMapper;
import com.smartshift.repository.EmployeeDeviceRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.EmployeeDeviceService;
import com.smartshift.service.NotificationService;
import com.smartshift.service.SchedulingAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeDeviceServiceImpl implements EmployeeDeviceService {

    private final EmployeeDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SchedulingAccessService schedulingAccessService;
    private final EmployeeDeviceMapper deviceMapper;
    private final Clock clock;

    /**
     * Validates the device fingerprint on check-in / check-out.
     *
     * <ul>
     *   <li>NEW   → registers PENDING, saves explanation, notifies managers,
     *               returns {@code false} (unverified — check-in still proceeds).</li>
     *   <li>PENDING → updates lastSeenAt + explanation if provided,
     *                 returns {@code false} (still unverified).</li>
     *   <li>APPROVED → updates lastSeenAt, returns {@code true} (verified).</li>
     *   <li>REVOKED → throws {@link BusinessRuleException} (hard block).</li>
     * </ul>
     */
    @Override
    @Transactional
    public boolean validateAndRegisterDevice(
        String username,
        DeviceCheckInRequest request
    ) {
        User employee = findEmployee(username);
        Instant now = clock.instant();

        EmployeeDevice existing = deviceRepository
            .findByUserIdAndFingerprintForUpdate(
                employee.getId(),
                request.deviceFingerprint()
            )
            .orElse(null);

        if (existing == null) {
            // Brand-new device → register as PENDING, allow check-in (unverified)
            EmployeeDevice device = new EmployeeDevice();
            device.setUser(employee);
            device.setDeviceFingerprint(request.deviceFingerprint());
            device.setDeviceLabel(normalizeText(request.deviceLabel()));
            device.setDeviceExplanation(normalizeText(request.deviceExplanation()));
            device.setStatus(DeviceStatus.PENDING);
            device.setRegisteredAt(now);
            device.setLastSeenAt(now);
            EmployeeDevice saved = deviceRepository.save(device);
            notifyManagersOfNewDevice(employee, saved);
            return false;
        }

        return switch (existing.getStatus()) {
            case REVOKED -> throw new BusinessRuleException(
                "Thiết bị này đã bị thu hồi quyền chấm công. "
                    + "Vui lòng liên hệ quản lý để được hỗ trợ."
            );
            case PENDING -> {
                // Still awaiting approval — update lastSeenAt and explanation if provided
                existing.setLastSeenAt(now);
                if (request.deviceExplanation() != null
                    && !request.deviceExplanation().isBlank()
                    && existing.getDeviceExplanation() == null) {
                    existing.setDeviceExplanation(
                        normalizeText(request.deviceExplanation())
                    );
                }
                deviceRepository.save(existing);
                yield false;
            }
            case APPROVED -> {
                existing.setLastSeenAt(now);
                deviceRepository.save(existing);
                yield true;
            }
        };
    }

    @Override
    public List<EmployeeDeviceResponse> getMyDevices(String username) {
        User employee = findEmployee(username);
        return deviceRepository.findAllByUserId(employee.getId())
            .stream()
            .map(deviceMapper::toResponse)
            .toList();
    }

    @Override
    public List<EmployeeDeviceResponse> getDevices(
        String username,
        Long locationId,
        DeviceStatus status
    ) {
        Long effectiveLocationId = schedulingAccessService
            .resolveLocationFilter(username, locationId);
        return deviceRepository
            .findAllByLocationIdAndStatus(effectiveLocationId, status)
            .stream()
            .map(deviceMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public EmployeeDeviceResponse reviewDevice(
        String username,
        Long deviceId,
        DeviceReviewRequest request
    ) {
        User reviewer = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));

        EmployeeDevice device = deviceRepository.findByIdForUpdate(deviceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy thiết bị với id " + deviceId
            ));

        validateReviewerLocation(reviewer, device);

        Instant now = clock.instant();
        boolean approved = Boolean.TRUE.equals(request.approved());
        device.setStatus(approved ? DeviceStatus.APPROVED : DeviceStatus.REVOKED);
        device.setApprovedBy(reviewer);
        device.setApprovedAt(now);
        EmployeeDevice saved = deviceRepository.save(device);

        notifyEmployeeOfReview(device.getUser(), saved, approved);
        return deviceMapper.toResponse(saved);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private User findEmployee(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));
    }

    private void validateReviewerLocation(User reviewer, EmployeeDevice device) {
        String reviewerRole = reviewer.getRole().getName();
        if ("ROLE_ADMIN".equals(reviewerRole)) {
            return;
        }
        if (!"ROLE_MANAGER".equals(reviewerRole)) {
            throw new AccessDeniedException(
                "Bạn không có quyền duyệt thiết bị"
            );
        }
        Long reviewerLocationId = reviewer.getLocation().getId();
        Long deviceLocationId = device.getUser().getLocation().getId();
        if (!Objects.equals(reviewerLocationId, deviceLocationId)) {
            throw new AccessDeniedException(
                "Quản lý chỉ được duyệt thiết bị trong chi nhánh của mình"
            );
        }
    }

    private void notifyManagersOfNewDevice(User employee, EmployeeDevice device) {
        List<User> managers = userRepository
            .findActiveNotificationReviewersForLocation(
                employee.getLocation().getId()
            );
        String explanationNote = device.getDeviceExplanation() != null
            ? " Lý do: \"" + device.getDeviceExplanation() + "\""
            : "";
        notificationService.createNotifications(
            managers,
            NotificationType.DEVICE_REGISTERED,
            "Thiết bị mới cần phê duyệt",
            "Nhân viên " + employee.getFullName()
                + " đã dùng thiết bị mới \""
                + deviceDisplayName(device)
                + "\" để chấm công và đang chờ phê duyệt."
                + explanationNote,
            NotificationReferenceType.EMPLOYEE_DEVICE,
            device.getId()
        );
    }

    private void notifyEmployeeOfReview(
        User employee,
        EmployeeDevice device,
        boolean approved
    ) {
        String label = deviceDisplayName(device);
        notificationService.createNotification(
            employee,
            approved ? NotificationType.DEVICE_APPROVED : NotificationType.DEVICE_REVOKED,
            approved ? "Thiết bị đã được phê duyệt" : "Thiết bị đã bị thu hồi",
            approved
                ? "Thiết bị \"" + label + "\" của bạn đã được phê duyệt. "
                    + "Các lần chấm công từ thiết bị này sẽ được xác nhận bình thường."
                : "Thiết bị \"" + label + "\" của bạn đã bị thu hồi quyền chấm công. "
                    + "Vui lòng liên hệ quản lý để biết thêm chi tiết.",
            NotificationReferenceType.EMPLOYEE_DEVICE,
            device.getId()
        );
    }

    private String deviceDisplayName(EmployeeDevice device) {
        if (device.getDeviceLabel() != null) {
            return device.getDeviceLabel();
        }
        String fp = device.getDeviceFingerprint();
        return fp.length() > 8 ? fp.substring(0, 8) + "..." : fp;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
