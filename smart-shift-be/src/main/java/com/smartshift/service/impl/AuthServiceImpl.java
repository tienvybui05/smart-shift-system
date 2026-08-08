package com.smartshift.service.impl;

import com.smartshift.config.JwtProperties;
import com.smartshift.dto.auth.CurrentUserResponse;
import com.smartshift.dto.auth.LoginRequest;
import com.smartshift.dto.auth.LoginResponse;
import com.smartshift.entity.User;
import com.smartshift.exception.InvalidCredentialsException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.AuthMapper;
import com.smartshift.repository.UserRepository;
import com.smartshift.security.JwtService;
import com.smartshift.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthMapper authMapper;

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();

        try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                    username,
                    request.password()
                )
            );
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException(
                "Tên đăng nhập hoặc mật khẩu không chính xác"
            );
        }

        User user = findUserByUsername(username);
        String accessToken = jwtService.generateAccessToken(user);

        return new LoginResponse(
            accessToken,
            "Bearer",
            jwtProperties.accessTokenExpiration().toSeconds(),
            authMapper.toCurrentUserResponse(user)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(String username) {
        return authMapper.toCurrentUserResponse(findUserByUsername(username));
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
    }
}
