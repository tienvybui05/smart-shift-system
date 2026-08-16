package com.smartshift.repository;

import com.smartshift.entity.SchedulePeriod;
import com.smartshift.enums.SchedulePeriodStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SchedulePeriodRepository extends JpaRepository<SchedulePeriod, Long> {

    @Query("""
        SELECT schedulePeriod
        FROM SchedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        JOIN FETCH schedulePeriod.createdBy createdBy
        LEFT JOIN FETCH schedulePeriod.publishedBy publishedBy
        WHERE (:locationId IS NULL OR location.id = :locationId)
          AND (:status IS NULL OR schedulePeriod.status = :status)
        ORDER BY schedulePeriod.startDate DESC, location.name ASC
        """)
    List<SchedulePeriod> search(
        @Param("locationId") Long locationId,
        @Param("status") SchedulePeriodStatus status
    );

    @Query("""
        SELECT CASE WHEN COUNT(schedulePeriod) > 0 THEN TRUE ELSE FALSE END
        FROM SchedulePeriod schedulePeriod
        WHERE schedulePeriod.location.id = :locationId
          AND schedulePeriod.startDate <= :endDate
          AND schedulePeriod.endDate >= :startDate
          AND (:excludedId IS NULL OR schedulePeriod.id <> :excludedId)
        """)
    boolean existsOverlappingPeriod(
        @Param("locationId") Long locationId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("excludedId") Long excludedId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT schedulePeriod
        FROM SchedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        JOIN FETCH schedulePeriod.createdBy createdBy
        LEFT JOIN FETCH schedulePeriod.publishedBy publishedBy
        WHERE schedulePeriod.id = :id
        """)
    Optional<SchedulePeriod> findByIdForUpdate(@Param("id") Long id);
}
