package com.smartshift.entity;

import com.smartshift.enums.EmploymentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_code", nullable = false, unique = true, length = 30)
    private String employeeCode;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private Position position;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(
        name = "min_hours_per_week",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal minHoursPerWeek = BigDecimal.ZERO;

    @Column(
        name = "max_hours_per_week",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal maxHoursPerWeek;

    @Column(
        name = "max_hours_per_day",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal maxHoursPerDay;

    @Column(
        name = "min_rest_hours",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal minRestHours;

    @Column(name = "max_consecutive_days", nullable = false)
    private Short maxConsecutiveDays;

    @Column(
        name = "hourly_rate",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal hourlyRate = BigDecimal.ZERO;

    @Column(
        name = "salary_coefficient",
        nullable = false,
        precision = 5,
        scale = 2
    )
    private BigDecimal salaryCoefficient = BigDecimal.ONE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
