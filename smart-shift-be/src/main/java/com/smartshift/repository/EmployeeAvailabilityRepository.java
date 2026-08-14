package com.smartshift.repository;

import com.smartshift.entity.EmployeeAvailability;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface EmployeeAvailabilityRepository extends JpaRepository<EmployeeAvailability, Long> {

    @EntityGraph(attributePaths = {"user", "user.location", "user.position"})
    List<EmployeeAvailability> findAllByUserIdAndAvailableDateBetweenOrderByAvailableDateAscStartTimeAsc(
        Long userId,
        LocalDate startDate,
        LocalDate endDate
    );

    @Query("""
        SELECT CASE WHEN COUNT(availability) > 0 THEN TRUE ELSE FALSE END
        FROM EmployeeAvailability availability
        WHERE availability.user.id = :userId
          AND availability.availableDate = :availableDate
          AND availability.startTime < :endTime
          AND availability.endTime > :startTime
          AND (:excludedId IS NULL OR availability.id <> :excludedId)
        """)
    boolean existsOverlappingSlot(
        @Param("userId") Long userId,
        @Param("availableDate") LocalDate availableDate,
        @Param("startTime") LocalTime startTime,
        @Param("endTime") LocalTime endTime,
        @Param("excludedId") Long excludedId
    );
}
