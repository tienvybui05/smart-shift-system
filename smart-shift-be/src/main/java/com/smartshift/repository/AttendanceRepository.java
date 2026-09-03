package com.smartshift.repository;

import com.smartshift.entity.Attendance;
import com.smartshift.enums.AttendanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    boolean existsByShiftAssignmentId(Long shiftAssignmentId);

    @Modifying
    @Query(value = """
        INSERT INTO attendances (
            shift_assignment_id,
            break_minutes,
            status,
            created_at,
            updated_at
        )
        SELECT
            assignment.id,
            work_shift.break_minutes,
            'ABSENT',
            :reconciledAt,
            :reconciledAt
        FROM shift_assignments assignment
        JOIN work_shifts work_shift
          ON work_shift.id = assignment.work_shift_id
        JOIN schedule_periods schedule_period
          ON schedule_period.id = work_shift.schedule_period_id
        WHERE assignment.status IN ('ASSIGNED', 'CONFIRMED')
          AND work_shift.status IN ('OPEN', 'FILLED', 'COMPLETED')
          AND schedule_period.status IN ('PUBLISHED', 'LOCKED')
          AND work_shift.end_at <= :reconciledAt
          AND NOT EXISTS (
              SELECT 1
              FROM attendances attendance
              WHERE attendance.shift_assignment_id = assignment.id
          )
        ON CONFLICT (shift_assignment_id) DO NOTHING
        """, nativeQuery = true)
    int createAbsencesForEndedShifts(
        @Param("reconciledAt") Instant reconciledAt
    );

    Optional<Attendance> findByShiftAssignmentId(Long shiftAssignmentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT attendance
        FROM Attendance attendance
        JOIN FETCH attendance.shiftAssignment assignment
        JOIN FETCH assignment.user employee
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH attendance.approvedBy approvedBy
        WHERE assignment.id = :shiftAssignmentId
        """)
    Optional<Attendance> findDetailedByShiftAssignmentIdForUpdate(
        @Param("shiftAssignmentId") Long shiftAssignmentId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT attendance
        FROM Attendance attendance
        JOIN FETCH attendance.shiftAssignment assignment
        JOIN FETCH assignment.user employee
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH attendance.approvedBy approvedBy
        WHERE attendance.id = :id
        """)
    Optional<Attendance> findDetailedByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT attendance
        FROM Attendance attendance
        JOIN FETCH attendance.shiftAssignment assignment
        JOIN FETCH assignment.user employee
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH attendance.approvedBy approvedBy
        WHERE employee.username = :username
          AND workShift.startAt >= :rangeStart
          AND workShift.startAt < :rangeEnd
        ORDER BY workShift.startAt DESC
        """)
    List<Attendance> findMyAttendancesInRange(
        @Param("username") String username,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );

    @Query("""
        SELECT attendance
        FROM Attendance attendance
        JOIN FETCH attendance.shiftAssignment assignment
        JOIN FETCH assignment.user employee
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH attendance.approvedBy approvedBy
        WHERE (:locationId IS NULL OR location.id = :locationId)
          AND (:status IS NULL OR attendance.status = :status)
          AND workShift.startAt < :rangeEnd
          AND workShift.endAt > :rangeStart
        ORDER BY workShift.startAt DESC, employee.fullName ASC
        """)
    List<Attendance> search(
        @Param("locationId") Long locationId,
        @Param("status") AttendanceStatus status,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );

    @Query("""
        SELECT attendance
        FROM Attendance attendance
        JOIN FETCH attendance.shiftAssignment assignment
        JOIN FETCH assignment.user employee
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        WHERE location.id = :locationId
          AND attendance.approvedAt IS NOT NULL
          AND attendance.checkInAt IS NOT NULL
          AND attendance.checkOutAt IS NOT NULL
          AND workShift.startAt < :rangeEnd
          AND workShift.endAt > :rangeStart
        ORDER BY employee.id ASC, workShift.startAt ASC
        """)
    List<Attendance> findApprovedCompletedForPayroll(
        @Param("locationId") Long locationId,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );
}
