package com.smartshift.repository;

import com.smartshift.entity.WorkShift;
import com.smartshift.enums.WorkShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {

    @Query("""
        SELECT workShift
        FROM WorkShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH workShift.shiftTemplate shiftTemplate
        WHERE schedulePeriod.id = :schedulePeriodId
          AND (:status IS NULL OR workShift.status = :status)
        ORDER BY workShift.startAt ASC
        """)
    List<WorkShift> search(
        @Param("schedulePeriodId") Long schedulePeriodId,
        @Param("status") WorkShiftStatus status
    );

    boolean existsBySchedulePeriodIdAndStartAt(
        Long schedulePeriodId,
        Instant startAt
    );

    boolean existsBySchedulePeriodIdAndStartAtAndIdNot(
        Long schedulePeriodId,
        Instant startAt,
        Long id
    );

    List<WorkShift> findAllBySchedulePeriodIdAndShiftTemplateIdAndStatus(
        Long schedulePeriodId,
        Long shiftTemplateId,
        WorkShiftStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT workShift
        FROM WorkShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        WHERE workShift.id = :id
        """)
    Optional<WorkShift> findByIdForUpdate(@Param("id") Long id);
}
