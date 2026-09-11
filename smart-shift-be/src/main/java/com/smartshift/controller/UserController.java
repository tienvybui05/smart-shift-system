package com.smartshift.controller;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.user.ChangePasswordRequest;
import com.smartshift.dto.user.CreateUserRequest;
import com.smartshift.dto.user.ResetPasswordRequest;
import com.smartshift.dto.user.UpdateUserRequest;
import com.smartshift.dto.user.UpdateEmployeeWorkProfileRequest;
import com.smartshift.dto.user.UserResponse;
import com.smartshift.dto.user.UserStatusRequest;
import com.smartshift.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> getUsers(
        @RequestParam(defaultValue = "0")
        @Min(value = 0, message = "Số trang không được âm")
        int page,

        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "Kích thước trang phải lớn hơn 0")
        @Max(value = 100, message = "Kích thước trang không được vượt quá 100")
        int size,

        @RequestParam(required = false) String keyword,

        @RequestParam(required = false)
        @Positive(message = "Id chi nhánh phải lớn hơn 0")
        Long locationId,

        @RequestParam(required = false)
        @Positive(message = "Id vị trí phải lớn hơn 0")
        Long positionId,

        @RequestParam(required = false) Boolean active,

        Authentication authentication
    ) {
        return ResponseEntity.ok(
            userService.getUsers(
                page,
                size,
                keyword,
                locationId,
                positionId,
                active,
                authentication.getName()
            )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
        @PathVariable Long id,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            userService.getUserById(id, authentication.getName())
        );
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
        @Valid @RequestBody CreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            userService.updateUser(id, request, authentication.getName())
        );
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(
        @PathVariable Long id,
        @Valid @RequestBody UserStatusRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            userService.updateStatus(
                id,
                request.active(),
                authentication.getName()
            )
        );
    }

    @PatchMapping("/{id}/work-profile")
    public ResponseEntity<UserResponse> updateEmployeeWorkProfile(
        @PathVariable Long id,
        @Valid @RequestBody UpdateEmployeeWorkProfileRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(
            userService.updateEmployeeWorkProfile(
                id,
                request,
                authentication.getName()
            )
        );
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
        @Valid @RequestBody ChangePasswordRequest request,
        Authentication authentication
    ) {
        userService.changePassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(
        @PathVariable Long id,
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
