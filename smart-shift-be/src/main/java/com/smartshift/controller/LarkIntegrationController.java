package com.smartshift.controller;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.lark.LarkDeliveryResponse;
import com.smartshift.dto.lark.LarkIntegrationStatusResponse;
import com.smartshift.dto.lark.LarkUserLinkResponse;
import com.smartshift.dto.lark.LarkUserSyncSummaryResponse;
import com.smartshift.enums.LarkDeliveryStatus;
import com.smartshift.service.LarkIntegrationService;
import com.smartshift.service.LarkUserLinkService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/integrations/lark")
@RequiredArgsConstructor
public class LarkIntegrationController {

    private final LarkIntegrationService larkIntegrationService;
    private final LarkUserLinkService larkUserLinkService;

    @GetMapping("/status")
    public ResponseEntity<LarkIntegrationStatusResponse> getStatus() {
        return ResponseEntity.ok(larkIntegrationService.getStatus());
    }

    @GetMapping("/deliveries")
    public ResponseEntity<PageResponse<LarkDeliveryResponse>> getDeliveries(
        @RequestParam(required = false) LarkDeliveryStatus status,
        @RequestParam(defaultValue = "0")
        @Min(value = 0, message = "Trang không được âm")
        int page,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "Số dòng mỗi trang phải lớn hơn 0")
        @Max(value = 100, message = "Chỉ được lấy tối đa 100 dòng mỗi trang")
        int size
    ) {
        return ResponseEntity.ok(
            larkIntegrationService.getDeliveries(status, page, size)
        );
    }

    @PostMapping("/test")
    public ResponseEntity<LarkDeliveryResponse> testConnection(
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            larkIntegrationService.enqueueGroupTest(authentication.getName())
        );
    }

    @GetMapping("/users")
    public ResponseEntity<PageResponse<LarkUserLinkResponse>> getUsers(
        @RequestParam(defaultValue = "0")
        @Min(value = 0, message = "Trang không được âm")
        int page,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "Số dòng mỗi trang phải lớn hơn 0")
        @Max(value = 100, message = "Chỉ được lấy tối đa 100 dòng mỗi trang")
        int size
    ) {
        return ResponseEntity.ok(larkUserLinkService.getUsers(page, size));
    }

    @PostMapping("/users/{id}/sync")
    public ResponseEntity<LarkUserLinkResponse> syncUser(
        @PathVariable
        @Positive(message = "Id nhân viên phải lớn hơn 0")
        Long id
    ) {
        return ResponseEntity.ok(larkUserLinkService.syncUser(id));
    }

    @PostMapping("/users/sync-all")
    public ResponseEntity<LarkUserSyncSummaryResponse> syncAllUsers() {
        return ResponseEntity.ok(larkUserLinkService.syncAllActiveUsers());
    }

    @PostMapping("/users/{id}/test")
    public ResponseEntity<LarkDeliveryResponse> testPersonalMessage(
        @PathVariable
        @Positive(message = "Id nhân viên phải lớn hơn 0")
        Long id
    ) {
        return ResponseEntity.ok(
            larkIntegrationService.enqueuePersonalTest(id)
        );
    }

    @PostMapping("/deliveries/{id}/retry")
    public ResponseEntity<LarkDeliveryResponse> retry(
        @PathVariable
        @Positive(message = "Id lần gửi phải lớn hơn 0")
        Long id
    ) {
        return ResponseEntity.ok(larkIntegrationService.retry(id));
    }
}
