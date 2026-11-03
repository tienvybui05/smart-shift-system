package com.smartshift.controller;

import com.smartshift.dto.device.DeviceReviewRequest;
import com.smartshift.dto.device.EmployeeDeviceResponse;
import com.smartshift.enums.DeviceStatus;
import com.smartshift.service.EmployeeDeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/employee-devices")
@RequiredArgsConstructor
public class EmployeeDeviceController {

    private final EmployeeDeviceService employeeDeviceService;

    /**
     * Employee: xem danh sách thiết bị của mình.
     */
    @GetMapping("/me")
    public ResponseEntity<List<EmployeeDeviceResponse>> getMyDevices(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            employeeDeviceService.getMyDevices(authentication.getName())
        );
    }

    /**
     * Manager/Admin: xem danh sách thiết bị theo chi nhánh và trạng thái.
     */
    @GetMapping
    public ResponseEntity<List<EmployeeDeviceResponse>> getDevices(
        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,
        @RequestParam(required = false) DeviceStatus status,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            employeeDeviceService.getDevices(
                authentication.getName(),
                locationId,
                status
            )
        );
    }

    /**
     * Manager/Admin: phê duyệt hoặc thu hồi thiết bị.
     */
    @PutMapping("/{id}/review")
    public ResponseEntity<EmployeeDeviceResponse> reviewDevice(
        @PathVariable
        @Positive(message = "Id thiết bị phải lớn hơn 0")
        Long id,
        @Valid @RequestBody DeviceReviewRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            employeeDeviceService.reviewDevice(
                authentication.getName(),
                id,
                request
            )
        );
    }
}
