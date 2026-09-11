package com.smartshift.service;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.user.ChangePasswordRequest;
import com.smartshift.dto.user.CreateUserRequest;
import com.smartshift.dto.user.ResetPasswordRequest;
import com.smartshift.dto.user.UpdateUserRequest;
import com.smartshift.dto.user.UpdateEmployeeWorkProfileRequest;
import com.smartshift.dto.user.UserResponse;

public interface UserService {

    PageResponse<UserResponse> getUsers(
        int page,
        int size,
        String keyword,
        Long locationId,
        Long positionId,
        Boolean active,
        String currentUsername
    );

    UserResponse getUserById(Long id, String currentUsername);

    UserResponse createUser(CreateUserRequest request);

    UserResponse updateUser(
        Long id,
        UpdateUserRequest request,
        String currentUsername
    );

    UserResponse updateStatus(Long id, boolean active, String currentUsername);

    UserResponse updateEmployeeWorkProfile(
        Long id,
        UpdateEmployeeWorkProfileRequest request,
        String currentUsername
    );

    void changePassword(String username, ChangePasswordRequest request);

    void resetPassword(Long id, ResetPasswordRequest request);
}
