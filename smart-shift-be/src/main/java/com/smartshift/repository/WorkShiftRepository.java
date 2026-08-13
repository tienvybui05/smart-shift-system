package com.smartshift.repository;

import com.smartshift.entity.WorkShift;
import com.smartshift.enums.WorkShiftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

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
}
