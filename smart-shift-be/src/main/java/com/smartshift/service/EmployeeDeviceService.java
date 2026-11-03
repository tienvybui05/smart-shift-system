package com.smartshift.service;

import com.smartshift.dto.device.DeviceCheckInRequest;
import com.smartshift.dto.device.DeviceReviewRequest;
import com.smartshift.dto.device.EmployeeDeviceResponse;
import com.smartshift.enums.DeviceStatus;

import java.util.List;

public interface EmployeeDeviceService {

    /**
     * Validates device fingerprint during check-in / check-out.
     * <ul>
     *   <li>First time seen → registers the device as PENDING, notifies manager, returns {@code false}.</li>
     *   <li>PENDING → updates lastSeenAt, returns {@code false} (still unverified).</li>
     *   <li>APPROVED → updates lastSeenAt, returns {@code true} (verified).</li>
     *   <li>REVOKED → throws BusinessRuleException.</li>
     * </ul>
     *
     * @return {@code true} if device is APPROVED, {@code false} if unverified (PENDING or new)
     */
    boolean validateAndRegisterDevice(String username, DeviceCheckInRequest request);

    /**
     * Returns all devices belonging to the current employee.
     */
    List<EmployeeDeviceResponse> getMyDevices(String username);

    /**
     * Manager: list devices in their location filtered by status.
     */
    List<EmployeeDeviceResponse> getDevices(
        String username,
        Long locationId,
        DeviceStatus status
    );

    /**
     * Manager: approve or revoke a device registration.
     */
    EmployeeDeviceResponse reviewDevice(
        String username,
        Long deviceId,
        DeviceReviewRequest request
    );
}
