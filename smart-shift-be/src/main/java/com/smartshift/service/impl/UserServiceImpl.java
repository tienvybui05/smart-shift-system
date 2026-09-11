package com.smartshift.service.impl;

import com.smartshift.dto.common.PageResponse;
import com.smartshift.dto.user.ChangePasswordRequest;
import com.smartshift.dto.user.CreateUserRequest;
import com.smartshift.dto.user.ResetPasswordRequest;
import com.smartshift.dto.user.UpdateUserRequest;
import com.smartshift.dto.user.UpdateEmployeeWorkProfileRequest;
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
import org.springframework.security.access.AccessDeniedException;
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

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

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
        Boolean active,
        String currentUsername
    ) {
        User actor = findUserByUsername(currentUsername);
        Long effectiveLocationId = locationId;
        String roleName = null;
        if (MANAGER_ROLE.equals(actor.getRole().getName())) {
            Long managerLocationId = actor.getLocation().getId();
            if (locationId != null && !Objects.equals(locationId, managerLocationId)) {
                throw new AccessDeniedException(
                    "Quản lý chỉ được xem nhân viên thuộc chi nhánh của mình"
                );
            }
            effectiveLocationId = managerLocationId;
            roleName = EMPLOYEE_ROLE;
        }

        PageRequest pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.ASC, "fullName")
        );
        String normalizedKeyword = normalizeNullableText(keyword);
        Page<UserResponse> users = userRepository.search(
                normalizedKeyword == null ? "" : normalizedKeyword,
                effectiveLocationId,
                positionId,
                active,
                roleName,
                pageable
            )
            .map(userMapper::toResponse);
        return PageResponse.from(users);
    }

    @Override
    public UserResponse getUserById(Long id, String currentUsername) {
        User actor = findUserByUsername(currentUsername);
        User target = findUserById(id);
        if (MANAGER_ROLE.equals(actor.getRole().getName())
            && (!EMPLOYEE_ROLE.equals(target.getRole().getName())
                || !Objects.equals(
                    actor.getLocation().getId(),
                    target.getLocation().getId()
                ))) {
            throw new AccessDeniedException(
                "Quản lý chỉ được xem nhân viên thuộc chi nhánh của mình"
            );
        }
        return userMapper.toResponse(target);
    }

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());
        validateUniqueFields(username, email, null);

        Role role = findRoleById(request.roleId());
        validateRoleSpecificFields(
            role,
            request.employmentType(),
            request.hireDate(),
            request.minHoursPerWeek(),
            request.maxHoursPerWeek(),
            request.maxHoursPerDay(),
            request.minRestHours(),
            request.maxConsecutiveDays(),
            request.basePayAmount(),
            request.salaryCoefficient()
        );
        Location location = findActiveLocationById(request.locationId());
        Position position = resolvePosition(role, request.positionId());
        String employeeCode = generateEmployeeCode(role);

        User user = userMapper.toEntity(
            request,
            employeeCode,
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
        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        boolean updatingSelf = user.getUsername().equals(currentUsername);
        if (updatingSelf && !user.getUsername().equals(username)) {
            throw new BusinessRuleException(
                "Không thể đổi tên đăng nhập của tài khoản đang sử dụng"
            );
        }

        validateUniqueFields(username, email, user);
        Role role = findRoleById(request.roleId());
        validateRoleSpecificFields(
            role,
            request.employmentType(),
            request.hireDate(),
            request.minHoursPerWeek(),
            request.maxHoursPerWeek(),
            request.maxHoursPerDay(),
            request.minRestHours(),
            request.maxConsecutiveDays(),
            request.basePayAmount(),
            request.salaryCoefficient()
        );
        Location location = findActiveLocationById(request.locationId());
        Position position = resolvePosition(role, request.positionId());

        String previousEmail = user.getEmail();
        String previousPhoneNumber = user.getPhoneNumber();
        userMapper.updateEntity(request, user, role, location, position);
        if (!Objects.equals(previousEmail, user.getEmail())
            || !Objects.equals(previousPhoneNumber, user.getPhoneNumber())) {
            user.setLarkOpenId(null);
            user.setLarkSyncedAt(null);
            user.setLarkSyncError(null);
        }
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
    public UserResponse updateEmployeeWorkProfile(
        Long id,
        UpdateEmployeeWorkProfileRequest request,
        String currentUsername
    ) {
        User manager = findUserByUsername(currentUsername);
        if (!MANAGER_ROLE.equals(manager.getRole().getName())) {
            throw new AccessDeniedException(
                "Chỉ quản lý được cập nhật thông tin công việc của nhân viên"
            );
        }

        User employee = findUserById(id);
        if (!EMPLOYEE_ROLE.equals(employee.getRole().getName())
            || !Objects.equals(
                manager.getLocation().getId(),
                employee.getLocation().getId()
            )) {
            throw new AccessDeniedException(
                "Quản lý chỉ được cập nhật nhân viên thuộc chi nhánh của mình"
            );
        }

        validateWorkingHours(
            request.minHoursPerWeek(),
            request.maxHoursPerWeek()
        );
        employee.setPosition(findActivePositionById(request.positionId()));
        employee.setEmploymentType(request.employmentType());
        employee.setMinHoursPerWeek(request.minHoursPerWeek());
        employee.setMaxHoursPerWeek(request.maxHoursPerWeek());
        employee.setMaxHoursPerDay(request.maxHoursPerDay());
        employee.setMinRestHours(request.minRestHours());
        employee.setMaxConsecutiveDays(request.maxConsecutiveDays());
        return userMapper.toResponse(userRepository.save(employee));
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
        String username,
        String email,
        User currentUser
    ) {
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

    private String generateEmployeeCode(Role role) {
        boolean admin = ADMIN_ROLE.equals(role.getName());
        Long sequenceValue = admin
            ? userRepository.nextAdminCodeSequenceValue()
            : userRepository.nextEmployeeCodeSequenceValue();
        return String.format(
            Locale.ROOT,
            "%s%06d",
            admin ? "AD" : "NV",
            sequenceValue
        );
    }

    private Position resolvePosition(Role role, Long positionId) {
        if (!EMPLOYEE_ROLE.equals(role.getName())) {
            return null;
        }
        if (positionId == null) {
            throw new BusinessRuleException(
                "Nhân viên phải được gán một vị trí làm việc"
            );
        }
        return findActivePositionById(positionId);
    }

    private void validateWorkingHours(BigDecimal minimum, BigDecimal maximum) {
        if (minimum.compareTo(maximum) > 0) {
            throw new BusinessRuleException(
                "Giờ tối thiểu mỗi tuần không được lớn hơn giờ tối đa"
            );
        }
    }

    private void validateRoleSpecificFields(
        Role role,
        com.smartshift.enums.EmploymentType employmentType,
        java.time.LocalDate hireDate,
        BigDecimal minHoursPerWeek,
        BigDecimal maxHoursPerWeek,
        BigDecimal maxHoursPerDay,
        BigDecimal minRestHours,
        Short maxConsecutiveDays,
        BigDecimal basePayAmount,
        BigDecimal salaryCoefficient
    ) {
        if (ADMIN_ROLE.equals(role.getName())) {
            return;
        }
        if (employmentType == null || hireDate == null
            || basePayAmount == null || salaryCoefficient == null) {
            throw new BusinessRuleException(
                "Vui lòng nhập đầy đủ thông tin hợp đồng và lương"
            );
        }
        if (!EMPLOYEE_ROLE.equals(role.getName())) {
            return;
        }
        if (minHoursPerWeek == null || maxHoursPerWeek == null
            || maxHoursPerDay == null || minRestHours == null
            || maxConsecutiveDays == null) {
            throw new BusinessRuleException(
                "Vui lòng nhập đầy đủ quy tắc giờ làm của nhân viên"
            );
        }
        validateWorkingHours(minHoursPerWeek, maxHoursPerWeek);
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
