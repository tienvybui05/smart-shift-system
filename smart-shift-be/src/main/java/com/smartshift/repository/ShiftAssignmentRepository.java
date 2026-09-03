package com.smartshift.repository;

import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.enums.AssignmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = {
        "user", "user.role", "user.location", "user.position",
        "position", "workShift", "workShift.shiftTemplate",
        "workShift.schedulePeriod", "workShift.schedulePeriod.location"
    })
    @Query("SELECT assignment FROM ShiftAssignment assignment WHERE assignment.id = :id")
    Optional<ShiftAssignment> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {
        "user", "user.role", "user.location", "user.position",
        "position", "workShift", "workShift.shiftTemplate",
        "workShift.schedulePeriod", "workShift.schedulePeriod.location"
    })
    @Query("""
        SELECT assignment
        FROM ShiftAssignment assignment
        WHERE assignment.workShift.schedulePeriod.id = :schedulePeriodId
          AND assignment.position.id = :positionId
          AND assignment.user.id <> :requesterUserId
          AND assignment.status IN :statuses
          AND assignment.workShift.startAt > :now
          AND assignment.workShift.status <> com.smartshift.enums.WorkShiftStatus.CANCELLED
        ORDER BY assignment.workShift.startAt ASC, assignment.user.fullName ASC
        """)
    List<ShiftAssignment> findSwapCandidates(
        @Param("schedulePeriodId") Long schedulePeriodId,
        @Param("positionId") Long positionId,
        @Param("requesterUserId") Long requesterUserId,
        @Param("statuses") Collection<AssignmentStatus> statuses,
        @Param("now") Instant now
    );

    @Query("SELECT assignment.workShift.id FROM ShiftAssignment assignment WHERE assignment.id = :id")
    Optional<Long> findWorkShiftIdByAssignmentId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
        "user",
        "workShift",
        "workShift.schedulePeriod",
        "workShift.schedulePeriod.location"
    })
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

    @Query("""
        SELECT DISTINCT assignment.user
        FROM ShiftAssignment assignment
        WHERE assignment.workShift.schedulePeriod.id = :schedulePeriodId
          AND assignment.status IN :statuses
        ORDER BY assignment.user.id ASC
        """)
    List<User> findDistinctAssignedUsersBySchedulePeriodId(
        @Param("schedulePeriodId") Long schedulePeriodId,
        @Param("statuses") Collection<AssignmentStatus> statuses
    );

    long countByWorkShiftIdAndPositionIdAndStatusIn(
        Long workShiftId,
        Long positionId,
        Collection<AssignmentStatus> statuses
    );
}
