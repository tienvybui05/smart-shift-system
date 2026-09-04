package com.smartshift.repository;

import com.smartshift.entity.WorkShift;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    @Query("""
        SELECT workShift
        FROM WorkShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH workShift.shiftTemplate shiftTemplate
        WHERE (:locationId IS NULL OR location.id = :locationId)
          AND schedulePeriod.status IN :periodStatuses
          AND workShift.status <> com.smartshift.enums.WorkShiftStatus.CANCELLED
          AND workShift.startAt < :rangeEnd
          AND workShift.endAt > :rangeStart
        ORDER BY workShift.startAt ASC, location.name ASC
        """)
    List<WorkShift> findDashboardShifts(
        @Param("locationId") Long locationId,
        @Param("periodStatuses") Collection<SchedulePeriodStatus> periodStatuses,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
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

    @Query("SELECT workShift.schedulePeriod.id FROM WorkShift workShift WHERE workShift.id = :id")
    Optional<Long> findSchedulePeriodIdById(@Param("id") Long id);

    List<WorkShift> findAllBySchedulePeriodIdAndShiftTemplateIdAndStatus(
        Long schedulePeriodId,
        Long shiftTemplateId,
        WorkShiftStatus status
    );

    @Query("""
        SELECT DISTINCT workShift
        FROM WorkShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH workShift.shiftTemplate shiftTemplate
        JOIN ShiftRequirement requirement
          ON requirement.workShift = workShift
        WHERE location.id = :locationId
          AND requirement.position.id = :positionId
          AND schedulePeriod.status = :periodStatus
          AND workShift.status = :shiftStatus
          AND workShift.startAt > :now
        ORDER BY workShift.startAt ASC
        """)
    List<WorkShift> findClaimableShifts(
        @Param("locationId") Long locationId,
        @Param("positionId") Long positionId,
        @Param("periodStatus") SchedulePeriodStatus periodStatus,
        @Param("shiftStatus") WorkShiftStatus shiftStatus,
        @Param("now") Instant now
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
