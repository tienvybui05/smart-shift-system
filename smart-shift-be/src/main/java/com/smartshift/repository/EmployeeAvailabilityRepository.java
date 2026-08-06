package com.smartshift.repository;

import com.smartshift.entity.EmployeeAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeAvailabilityRepository extends JpaRepository<EmployeeAvailability, Long> {

    List<EmployeeAvailability> findAllByUserIdAndAvailableDateBetweenOrderByAvailableDateAscStartTimeAsc(
        Long userId,
        LocalDate startDate,
        LocalDate endDate
    );
}

