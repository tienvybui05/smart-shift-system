package com.smartshift.config;

import com.smartshift.entity.Location;
import com.smartshift.entity.Role;
import com.smartshift.entity.User;
import com.smartshift.enums.EmploymentType;
import com.smartshift.repository.LocationRepository;
import com.smartshift.repository.RoleRepository;
import com.smartshift.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "app.security.bootstrap-admin",
    name = "enabled",
    havingValue = "true"
)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String DEFAULT_LOCATION = "STORE_HCM_001";
    private static final String ADMIN_EMPLOYEE_CODE = "ADMIN001";

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LocationRepository locationRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String username = properties.username().trim();
        if (userRepository.existsByUsername(username)) {
            return;
        }

        Role role = roleRepository.findByName(ADMIN_ROLE)
            .orElseThrow(() -> missingSeedData("role", ADMIN_ROLE));
        Location location = locationRepository.findByCode(DEFAULT_LOCATION)
            .orElseThrow(() -> missingSeedData("location", DEFAULT_LOCATION));
        User admin = new User();
        admin.setEmployeeCode(ADMIN_EMPLOYEE_CODE);
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setFullName("System Administrator");
        admin.setRole(role);
        admin.setLocation(location);
        admin.setEmploymentType(EmploymentType.FULL_TIME);
        admin.setHireDate(LocalDate.now());
        admin.setMinHoursPerWeek(new BigDecimal("40.00"));
        admin.setMaxHoursPerWeek(new BigDecimal("48.00"));
        admin.setMaxHoursPerDay(new BigDecimal("8.00"));
        admin.setMinRestHours(new BigDecimal("12.00"));
        admin.setMaxConsecutiveDays((short) 6);
        admin.setActive(true);

        userRepository.save(admin);
        log.warn(
            "Created bootstrap administrator '{}'. Change its password before production use.",
            username
        );
    }

    private IllegalStateException missingSeedData(String type, String value) {
        return new IllegalStateException(
            "Cannot create bootstrap admin: missing " + type + " '" + value + "'"
        );
    }
}
