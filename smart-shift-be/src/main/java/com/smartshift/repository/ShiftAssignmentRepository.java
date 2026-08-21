package com.smartshift.repository;

import com.smartshift.entity.ShiftAssignment;
import com.smartshift.enums.AssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @Query("""
        SELECT assignment
        FROM ShiftAssignment assignment
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH assignment.user user
        JOIN FETCH user.position userPosition
        JOIN FETCH assignment.position position
        LEFT JOIN FETCH assignment.assignedBy assignedBy
        WHERE workShift.id = :workShiftId
          AND assignment.status IN :statuses
        ORDER BY position.name ASC, user.fullName ASC
        """)
    List<ShiftAssignment> findAllDetailedByWorkShiftIdAndStatusIn(
        @Param("workShiftId") Long workShiftId,
        @Param("statuses") Collection<AssignmentStatus> statuses
    );

    List<ShiftAssignment> findAllByUserIdAndStatus(Long userId, AssignmentStatus status);

    Optional<ShiftAssignment> findByWorkShiftIdAndUserId(Long workShiftId, Long userId);

    @Query("SELECT assignment.workShift.id FROM ShiftAssignment assignment WHERE assignment.id = :id")
    Optional<Long> findWorkShiftIdByAssignmentId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT assignment FROM ShiftAssignment assignment WHERE assignment.id = :id")
    Optional<ShiftAssignment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT assignment
        FROM ShiftAssignment assignment
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        WHERE assignment.user.id = :userId
          AND assignment.status IN :statuses
          AND workShift.startAt < :rangeEnd
          AND workShift.endAt > :rangeStart
        ORDER BY workShift.startAt ASC
        """)
    List<ShiftAssignment> findActiveAssignmentsInRange(
        @Param("userId") Long userId,
        @Param("statuses") Collection<AssignmentStatus> statuses,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );

    @Query("""
        SELECT assignment
        FROM ShiftAssignment assignment
        JOIN FETCH assignment.workShift workShift
        JOIN FETCH workShift.schedulePeriod schedulePeriod
        JOIN FETCH schedulePeriod.location location
        LEFT JOIN FETCH workShift.shiftTemplate shiftTemplate
        JOIN FETCH assignment.position position
        WHERE assignment.user.username = :username
          AND assignment.status IN :statuses
          AND workShift.startAt < :rangeEnd
          AND workShift.endAt > :rangeStart
        ORDER BY workShift.startAt ASC
        """)
    List<ShiftAssignment> findMyScheduleInRange(
        @Param("username") String username,
        @Param("statuses") Collection<AssignmentStatus> statuses,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );

    long countByWorkShiftIdAndPositionIdAndStatusIn(
        Long workShiftId,
        Long positionId,
        Collection<AssignmentStatus> statuses
    );
}
