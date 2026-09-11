package com.smartshift.mapper;

import com.smartshift.dto.user.CreateUserRequest;
import com.smartshift.dto.user.UpdateUserRequest;
import com.smartshift.dto.user.UserResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.Role;
import com.smartshift.entity.User;
import com.smartshift.enums.EmploymentType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Component
public class UserMapper {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

    public User toEntity(
        CreateUserRequest request,
        String employeeCode,
        String passwordHash,
        Role role,
        Location location,
        Position position
    ) {
        User user = new User();
        user.setEmployeeCode(employeeCode);
        user.setPasswordHash(passwordHash);
        user.setActive(request.active());
        updateCommonFields(request, user, role, location, position);
        return user;
    }

    public void updateEntity(
        UpdateUserRequest request,
        User user,
        Role role,
        Location location,
        Position position
    ) {
        user.setUsername(normalizeUsername(request.username()));
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizeEmail(request.email()));
        user.setPhoneNumber(normalizeNullableText(request.phoneNumber()));
        user.setRole(role);
        user.setLocation(location);
        user.setPosition(position);
        updateRoleSpecificFields(
            user,
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
    }

    public UserResponse toResponse(User user) {
        Position position = user.getPosition();
        return new UserResponse(
            user.getId(),
            user.getEmployeeCode(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getRole().getId(),
            user.getRole().getName(),
            user.getLocation().getId(),
            user.getLocation().getCode(),
            user.getLocation().getName(),
            position == null ? null : position.getId(),
            position == null ? null : position.getCode(),
            position == null ? null : position.getName(),
            user.getEmploymentType(),
            user.getHireDate(),
            user.getMinHoursPerWeek(),
            user.getMaxHoursPerWeek(),
            user.getMaxHoursPerDay(),
            user.getMinRestHours(),
            user.getMaxConsecutiveDays(),
            user.getBasePayAmount(),
            user.getSalaryCoefficient(),
            user.isActive(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }

    private void updateCommonFields(
        CreateUserRequest request,
        User user,
        Role role,
        Location location,
        Position position
    ) {
        user.setUsername(normalizeUsername(request.username()));
        user.setFullName(request.fullName().trim());
        user.setEmail(normalizeEmail(request.email()));
        user.setPhoneNumber(normalizeNullableText(request.phoneNumber()));
        user.setRole(role);
        user.setLocation(location);
        user.setPosition(position);
        updateRoleSpecificFields(
            user,
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
    }

    private void updateRoleSpecificFields(
        User user,
        Role role,
        EmploymentType employmentType,
        LocalDate hireDate,
        BigDecimal minHoursPerWeek,
        BigDecimal maxHoursPerWeek,
        BigDecimal maxHoursPerDay,
        BigDecimal minRestHours,
        Short maxConsecutiveDays,
        BigDecimal basePayAmount,
        BigDecimal salaryCoefficient
    ) {
        boolean admin = ADMIN_ROLE.equals(role.getName());
        boolean employee = EMPLOYEE_ROLE.equals(role.getName());

        user.setEmploymentType(admin ? EmploymentType.FULL_TIME : employmentType);
        user.setHireDate(admin ? LocalDate.now() : hireDate);
        user.setMinHoursPerWeek(employee ? minHoursPerWeek : BigDecimal.ZERO);
        user.setMaxHoursPerWeek(employee ? maxHoursPerWeek : new BigDecimal("168"));
        user.setMaxHoursPerDay(employee ? maxHoursPerDay : new BigDecimal("24"));
        user.setMinRestHours(employee ? minRestHours : BigDecimal.ZERO);
        user.setMaxConsecutiveDays(employee ? maxConsecutiveDays : (short) 7);
        user.setBasePayAmount(admin ? BigDecimal.ZERO : basePayAmount);
        user.setSalaryCoefficient(admin ? BigDecimal.ONE : salaryCoefficient);
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
