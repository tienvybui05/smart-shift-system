package com.smartshift.service.impl;

import com.smartshift.dto.device.DeviceCheckInRequest;
import com.smartshift.dto.device.DeviceReviewRequest;
import com.smartshift.dto.device.EmployeeDeviceResponse;
import com.smartshift.entity.EmployeeDevice;
import com.smartshift.entity.Location;
import com.smartshift.entity.Role;
import com.smartshift.entity.User;
import com.smartshift.enums.DeviceStatus;
import com.smartshift.enums.NotificationReferenceType;
import com.smartshift.enums.NotificationType;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.mapper.EmployeeDeviceMapper;
import com.smartshift.repository.EmployeeDeviceRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.NotificationService;
import com.smartshift.service.SchedulingAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeDeviceServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");

    @Mock
    private EmployeeDeviceRepository deviceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private SchedulingAccessService schedulingAccessService;

    private EmployeeDeviceServiceImpl deviceService;
    private User employee;
    private Location location;

    @BeforeEach
    void setUp() {
        deviceService = new EmployeeDeviceServiceImpl(
            deviceRepository,
            userRepository,
            notificationService,
            schedulingAccessService,
            new EmployeeDeviceMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        location = new Location();
        location.setId(1L);
        location.setName("Chi nhánh Q1");

        Role empRole = new Role();
        empRole.setId(3L);
        empRole.setName("ROLE_EMPLOYEE");

        employee = new User();
        employee.setId(10L);
        employee.setUsername("nhanvien");
        employee.setEmployeeCode("EMP001");
        employee.setFullName("Nguyen Van A");
        employee.setLocation(location);
        employee.setRole(empRole);
    }

    @Test
    void validateAndRegisterDevice_BrandNewDevice_RegistersAsPendingAndReturnsFalse() {
        when(userRepository.findByUsername("nhanvien")).thenReturn(Optional.of(employee));
        when(deviceRepository.findByUserIdAndFingerprintForUpdate(10L, "fp-new"))
            .thenReturn(Optional.empty());

        when(deviceRepository.save(any(EmployeeDevice.class)))
            .thenAnswer(inv -> {
                EmployeeDevice d = inv.getArgument(0);
                d.setId(100L);
                return d;
            });

        User manager = new User();
        manager.setId(2L);
        when(userRepository.findActiveNotificationReviewersForLocation(1L))
            .thenReturn(List.of(manager));

        DeviceCheckInRequest request = new DeviceCheckInRequest(
            "fp-new",
            "iPhone 15 Pro",
            "Mới đổi điện thoại do máy cũ bị hỏng"
        );

        boolean verified = deviceService.validateAndRegisterDevice("nhanvien", request);

        assertFalse(verified);
        verify(deviceRepository).save(any(EmployeeDevice.class));
        verify(notificationService).createNotifications(
            eq(List.of(manager)),
            eq(NotificationType.DEVICE_REGISTERED),
            eq("Thiết bị mới cần phê duyệt"),
            org.mockito.ArgumentMatchers.contains("Mới đổi điện thoại"),
            eq(NotificationReferenceType.EMPLOYEE_DEVICE),
            eq(100L)
        );
    }

    @Test
    void validateAndRegisterDevice_ApprovedDevice_ReturnsTrue() {
        when(userRepository.findByUsername("nhanvien")).thenReturn(Optional.of(employee));

        EmployeeDevice existing = new EmployeeDevice();
        existing.setId(50L);
        existing.setUser(employee);
        existing.setDeviceFingerprint("fp-known");
        existing.setStatus(DeviceStatus.APPROVED);

        when(deviceRepository.findByUserIdAndFingerprintForUpdate(10L, "fp-known"))
            .thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(EmployeeDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceCheckInRequest request = new DeviceCheckInRequest(
            "fp-known",
            "iPhone 15 Pro",
            null
        );

        boolean verified = deviceService.validateAndRegisterDevice("nhanvien", request);

        assertTrue(verified);
        assertEquals(NOW, existing.getLastSeenAt());
    }

    @Test
    void validateAndRegisterDevice_PendingDevice_ReturnsFalseAndUpdatesExplanationIfMissing() {
        when(userRepository.findByUsername("nhanvien")).thenReturn(Optional.of(employee));

        EmployeeDevice existing = new EmployeeDevice();
        existing.setId(51L);
        existing.setUser(employee);
        existing.setDeviceFingerprint("fp-pending");
        existing.setStatus(DeviceStatus.PENDING);
        existing.setDeviceExplanation(null);

        when(deviceRepository.findByUserIdAndFingerprintForUpdate(10L, "fp-pending"))
            .thenReturn(Optional.of(existing));
        when(deviceRepository.save(any(EmployeeDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceCheckInRequest request = new DeviceCheckInRequest(
            "fp-pending",
            "iPhone 15 Pro",
            "Bổ sung giải trình máy mượn"
        );

        boolean verified = deviceService.validateAndRegisterDevice("nhanvien", request);

        assertFalse(verified);
        assertEquals("Bổ sung giải trình máy mượn", existing.getDeviceExplanation());
        assertEquals(NOW, existing.getLastSeenAt());
    }

    @Test
    void validateAndRegisterDevice_RevokedDevice_ThrowsBusinessRuleException() {
        when(userRepository.findByUsername("nhanvien")).thenReturn(Optional.of(employee));

        EmployeeDevice existing = new EmployeeDevice();
        existing.setId(52L);
        existing.setUser(employee);
        existing.setDeviceFingerprint("fp-blocked");
        existing.setStatus(DeviceStatus.REVOKED);

        when(deviceRepository.findByUserIdAndFingerprintForUpdate(10L, "fp-blocked"))
            .thenReturn(Optional.of(existing));

        DeviceCheckInRequest request = new DeviceCheckInRequest(
            "fp-blocked",
            "Blocked Phone",
            null
        );

        BusinessRuleException ex = assertThrows(
            BusinessRuleException.class,
            () -> deviceService.validateAndRegisterDevice("nhanvien", request)
        );
        assertTrue(ex.getMessage().contains("thu hồi"));
    }

    @Test
    void reviewDevice_Approved_UpdatesStatusAndNotifiesEmployee() {
        Role mgrRole = new Role();
        mgrRole.setName("ROLE_MANAGER");

        User manager = new User();
        manager.setId(2L);
        manager.setUsername("manager01");
        manager.setFullName("Quan Ly 01");
        manager.setLocation(location);
        manager.setRole(mgrRole);

        EmployeeDevice device = new EmployeeDevice();
        device.setId(100L);
        device.setUser(employee);
        device.setDeviceFingerprint("fp-pending");
        device.setDeviceLabel("Samsung Galaxy");
        device.setStatus(DeviceStatus.PENDING);

        when(userRepository.findByUsername("manager01")).thenReturn(Optional.of(manager));
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(EmployeeDevice.class))).thenAnswer(inv -> inv.getArgument(0));

        DeviceReviewRequest reviewRequest = new DeviceReviewRequest(true);
        EmployeeDeviceResponse res = deviceService.reviewDevice("manager01", 100L, reviewRequest);

        assertEquals(DeviceStatus.APPROVED, res.status());
        assertEquals(manager.getId(), res.approvedById());
        verify(notificationService).createNotification(
            eq(employee),
            eq(NotificationType.DEVICE_APPROVED),
            eq("Thiết bị đã được phê duyệt"),
            org.mockito.ArgumentMatchers.contains("Samsung Galaxy"),
            eq(NotificationReferenceType.EMPLOYEE_DEVICE),
            eq(100L)
        );
    }

    @Test
    void reviewDevice_WrongLocation_ThrowsAccessDenied() {
        Location otherLocation = new Location();
        otherLocation.setId(99L);

        Role mgrRole = new Role();
        mgrRole.setName("ROLE_MANAGER");

        User manager = new User();
        manager.setId(2L);
        manager.setUsername("manager_other");
        manager.setLocation(otherLocation);
        manager.setRole(mgrRole);

        EmployeeDevice device = new EmployeeDevice();
        device.setId(100L);
        device.setUser(employee);

        when(userRepository.findByUsername("manager_other")).thenReturn(Optional.of(manager));
        when(deviceRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(device));

        DeviceReviewRequest reviewRequest = new DeviceReviewRequest(true);
        assertThrows(
            AccessDeniedException.class,
            () -> deviceService.reviewDevice("manager_other", 100L, reviewRequest)
        );
    }
}
