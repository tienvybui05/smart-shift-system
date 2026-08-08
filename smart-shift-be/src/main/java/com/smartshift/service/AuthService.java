package com.smartshift.service;

import com.smartshift.dto.auth.CurrentUserResponse;
import com.smartshift.dto.auth.LoginRequest;
import com.smartshift.dto.auth.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    CurrentUserResponse getCurrentUser(String username);
}
