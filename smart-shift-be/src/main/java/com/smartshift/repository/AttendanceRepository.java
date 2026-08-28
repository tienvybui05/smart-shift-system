package com.smartshift.repository;

import com.smartshift.entity.Attendance;
import com.smartshift.enums.AttendanceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

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
          AND workShift.startAt < :rangeEnd
          AND workShift.endAt > :rangeStart
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
}
