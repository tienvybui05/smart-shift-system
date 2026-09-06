package com.smartshift.mapper;

import com.smartshift.dto.user.CreateUserRequest;
import com.smartshift.dto.user.UpdateUserRequest;
import com.smartshift.dto.user.UserResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.Position;
import com.smartshift.entity.Role;
import com.smartshift.entity.User;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class UserMapper {

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
        user.setEmploymentType(request.employmentType());
        user.setHireDate(request.hireDate());
        user.setMinHoursPerWeek(request.minHoursPerWeek());
        user.setMaxHoursPerWeek(request.maxHoursPerWeek());
        user.setMaxHoursPerDay(request.maxHoursPerDay());
        user.setMinRestHours(request.minRestHours());
        user.setMaxConsecutiveDays(request.maxConsecutiveDays());
        user.setHourlyRate(request.hourlyRate());
        user.setSalaryCoefficient(request.salaryCoefficient());
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
            user.getHourlyRate(),
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
        user.setEmploymentType(request.employmentType());
        user.setHireDate(request.hireDate());
        user.setMinHoursPerWeek(request.minHoursPerWeek());
        user.setMaxHoursPerWeek(request.maxHoursPerWeek());
        user.setMaxHoursPerDay(request.maxHoursPerDay());
        user.setMinRestHours(request.minRestHours());
        user.setMaxConsecutiveDays(request.maxConsecutiveDays());
        user.setHourlyRate(request.hourlyRate());
        user.setSalaryCoefficient(request.salaryCoefficient());
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
