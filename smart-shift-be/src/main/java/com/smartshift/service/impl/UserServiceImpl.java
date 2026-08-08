package com.smartshift.service.impl;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.user.ChangePasswordRequest;
import com.smartshift.dto.user.CreateUserRequest;
import com.smartshift.dto.user.ResetPasswordRequest;
import com.smartshift.dto.user.UpdateUserRequest;
import com.smartshift.dto.user.UserResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.Role;
import com.smartshift.entity.User;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.DuplicateResourceException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.UserMapper;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.PositionRepository;
import com.smartshift.repository.RoleRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LocationRepository locationRepository;
    private final PositionRepository positionRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResponse<UserResponse> getUsers(
        int page,
        int size,
        String keyword,
        Long locationId,
        Long positionId,
        Boolean active
    ) {
        PageRequest pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.ASC, "fullName")
        );
        String normalizedKeyword = normalizeNullableText(keyword);
        Page<UserResponse> users = userRepository.search(
                normalizedKeyword == null ? "" : normalizedKeyword,
                locationId,
                positionId,
                active,
                pageable
            )
            .map(userMapper::toResponse);
        return PageResponse.from(users);
    }

    @Override
    public UserResponse getUserById(Long id) {
        return userMapper.toResponse(findUserById(id));
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        validateWorkingHours(
            request.minHoursPerWeek(),
            request.maxHoursPerWeek()
        );

        String employeeCode = request.employeeCode().trim();
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        validateUniqueFields(employeeCode, username, email, null);

        Role role = findRoleById(request.roleId());
        Location location = findActiveLocationById(request.locationId());
        Position position = findActivePositionById(request.positionId());

        User user = userMapper.toEntity(
            request,
            passwordEncoder.encode(request.password()),
            role,
            location,
            position
        );
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateUser(
        Long id,
        UpdateUserRequest request,
        String currentUsername
    ) {
        User user = findUserById(id);
        validateWorkingHours(
            request.minHoursPerWeek(),
            request.maxHoursPerWeek()
        );

        String employeeCode = request.employeeCode().trim();
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        boolean updatingSelf = user.getUsername().equals(currentUsername);
        if (updatingSelf && !user.getUsername().equals(username)) {
            throw new BusinessRuleException(
                "Không thể đổi tên đăng nhập của tài khoản đang sử dụng"
            );
        }

        validateUniqueFields(employeeCode, username, email, user);
        Role role = findRoleById(request.roleId());
        Location location = findActiveLocationById(request.locationId());
        Position position = findActivePositionById(request.positionId());

        userMapper.updateEntity(request, user, role, location, position);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateStatus(
        Long id,
        boolean active,
        String currentUsername
    ) {
        User user = findUserById(id);
        if (!active && user.getUsername().equals(currentUsername)) {
            throw new BusinessRuleException(
                "Bạn không thể tự khóa tài khoản đang đăng nhập"
            );
        }

        user.setActive(active);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(
        String username,
        ChangePasswordRequest request
    ) {
        User user = findUserByUsername(username);
        if (!passwordEncoder.matches(
            request.currentPassword(),
            user.getPasswordHash()
        )) {
            throw new BusinessRuleException("Mật khẩu hiện tại không chính xác");
        }

        validateNewPassword(
            request.newPassword(),
            request.confirmPassword(),
            user
        );
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        User user = findUserById(id);
        validateNewPassword(
            request.newPassword(),
            request.confirmPassword(),
            user
        );
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private void validateUniqueFields(
        String employeeCode,
        String username,
        String email,
        User currentUser
    ) {
        boolean employeeCodeChanged = currentUser == null
            || !currentUser.getEmployeeCode().equals(employeeCode);
        if (employeeCodeChanged && userRepository.existsByEmployeeCode(employeeCode)) {
            throw new DuplicateResourceException(
                "Mã nhân viên '" + employeeCode + "' đã tồn tại"
            );
        }

        boolean usernameChanged = currentUser == null
            || !currentUser.getUsername().equals(username);
        if (usernameChanged && userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException(
                "Tên đăng nhập '" + username + "' đã tồn tại"
            );
        }

        boolean emailChanged = currentUser == null
            || !Objects.equals(currentUser.getEmail(), email);
        if (emailChanged && email != null && userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException(
                "Email '" + email + "' đã tồn tại"
            );
        }
    }

    private void validateWorkingHours(BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) > 0) {
            throw new BusinessRuleException(
                "Giờ tối thiểu mỗi tuần không được lớn hơn giờ tối đa"
            );
        }
    }

    private void validateNewPassword(
        String newPassword,
        String confirmPassword,
        User user
    ) {
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessRuleException("Xác nhận mật khẩu không khớp");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessRuleException(
                "Mật khẩu mới phải khác mật khẩu hiện tại"
            );
        }
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy nhân viên có id " + id
            ));
    }

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản '" + username + "'"
            ));
    }

    private Role findRoleById(Long id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy vai trò có id " + id
            ));
    }

    private Location findActiveLocationById(Long id) {
        Location location = locationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy chi nhánh có id " + id
            ));
        if (!location.isActive()) {
            throw new BusinessRuleException(
                "Không thể gán nhân viên vào chi nhánh đã ngừng hoạt động"
            );
        }
        return location;
    }

    private Position findActivePositionById(Long id) {
        Position position = positionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy vị trí có id " + id
            ));
        if (!position.isActive()) {
            throw new BusinessRuleException(
                "Không thể gán nhân viên vào vị trí đã ngừng hoạt động"
            );
        }
        return position;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        String normalized = normalizeNullableText(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
