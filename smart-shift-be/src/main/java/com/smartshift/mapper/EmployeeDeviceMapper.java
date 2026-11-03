package com.smartshift.mapper;

import com.smartshift.dto.device.EmployeeDeviceResponse;
import com.smartshift.entity.EmployeeDevice;
import com.smartshift.entity.User;
import org.springframework.stereotype.Component;

@Component
public class EmployeeDeviceMapper {

    public EmployeeDeviceResponse toResponse(EmployeeDevice device) {
        User employee = device.getUser();
        User approvedBy = device.getApprovedBy();
        return new EmployeeDeviceResponse(
            device.getId(),
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            device.getDeviceFingerprint(),
            device.getDeviceLabel(),
            device.getStatus(),
            device.getDeviceExplanation(),
            device.getRegisteredAt(),
            approvedBy == null ? null : approvedBy.getId(),
            approvedBy == null ? null : approvedBy.getFullName(),
            device.getApprovedAt(),
            device.getLastSeenAt(),
            device.getCreatedAt(),
            device.getUpdatedAt()
        );
    }
}
