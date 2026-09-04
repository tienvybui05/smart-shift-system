package com.smartshift.repository;

import com.smartshift.entity.ShiftSwapRequest;
import com.smartshift.enums.ShiftSwapStatus;
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

public interface ShiftSwapRequestRepository extends JpaRepository<ShiftSwapRequest, Long> {

    @Query("""
        SELECT COUNT(request)
        FROM ShiftSwapRequest request
        WHERE request.status IN :statuses
          AND (
            request.requesterUser.username = :username
            OR request.targetUser.username = :username
          )
        """)
    long countMineByStatuses(
        @Param("username") String username,
        @Param("statuses") Collection<ShiftSwapStatus> statuses
    );

    @Query("""
        SELECT COUNT(request)
        FROM ShiftSwapRequest request
        WHERE request.status = :status
          AND (:locationId IS NULL
            OR request.requesterAssignment.workShift.schedulePeriod.location.id = :locationId)
        """)
    long countByStatusAndScope(
        @Param("status") ShiftSwapStatus status,
        @Param("locationId") Long locationId
    );

    @EntityGraph(attributePaths = {
        "requesterUser", "requesterUser.role",
        "requesterAssignment", "requesterAssignment.position",
        "requesterAssignment.workShift",
        "requesterAssignment.workShift.shiftTemplate",
        "requesterAssignment.workShift.schedulePeriod",
        "requesterAssignment.workShift.schedulePeriod.location",
        "targetUser", "targetUser.role",
        "targetAssignment", "targetAssignment.position",
        "targetAssignment.workShift",
        "targetAssignment.workShift.shiftTemplate",
        "targetAssignment.workShift.schedulePeriod",
        "targetAssignment.workShift.schedulePeriod.location",
        "approvedBy"
    })
    @Query("""
        SELECT request
        FROM ShiftSwapRequest request
        WHERE request.requesterUser.username = :username
           OR request.targetUser.username = :username
        ORDER BY request.createdAt DESC
        """)
    List<ShiftSwapRequest> findMyDetailed(
        @Param("username") String username
    );

    @EntityGraph(attributePaths = {
        "requesterUser", "requesterUser.role",
        "requesterAssignment", "requesterAssignment.position",
        "requesterAssignment.workShift",
        "requesterAssignment.workShift.shiftTemplate",
        "requesterAssignment.workShift.schedulePeriod",
        "requesterAssignment.workShift.schedulePeriod.location",
        "targetUser", "targetUser.role",
        "targetAssignment", "targetAssignment.position",
        "targetAssignment.workShift",
        "targetAssignment.workShift.shiftTemplate",
        "targetAssignment.workShift.schedulePeriod",
        "targetAssignment.workShift.schedulePeriod.location",
        "approvedBy"
    })
    @Query("""
        SELECT request
        FROM ShiftSwapRequest request
        WHERE request.status = com.smartshift.enums.ShiftSwapStatus.PENDING
          AND request.targetUser IS NULL
          AND request.targetAssignment IS NULL
          AND request.requesterUser.id <> :userId
          AND request.requesterAssignment.position.id = :positionId
          AND request.requesterAssignment.workShift.schedulePeriod.location.id = :locationId
          AND request.requesterAssignment.workShift.schedulePeriod.status = com.smartshift.enums.SchedulePeriodStatus.PUBLISHED
          AND request.requesterAssignment.workShift.startAt > :now
        ORDER BY request.requesterAssignment.workShift.startAt ASC
        """)
    List<ShiftSwapRequest> findAvailableGiveawaysDetailed(
        @Param("userId") Long userId,
        @Param("locationId") Long locationId,
        @Param("positionId") Long positionId,
        @Param("now") Instant now
    );

    @EntityGraph(attributePaths = {
        "requesterUser", "requesterUser.role",
        "requesterAssignment", "requesterAssignment.position",
        "requesterAssignment.workShift",
        "requesterAssignment.workShift.shiftTemplate",
        "requesterAssignment.workShift.schedulePeriod",
        "requesterAssignment.workShift.schedulePeriod.location",
        "targetUser", "targetUser.role",
        "targetAssignment", "targetAssignment.position",
        "targetAssignment.workShift",
        "targetAssignment.workShift.shiftTemplate",
        "targetAssignment.workShift.schedulePeriod",
        "targetAssignment.workShift.schedulePeriod.location",
        "approvedBy"
    })
    @Query("""
        SELECT request
        FROM ShiftSwapRequest request
        WHERE (:status IS NULL OR request.status = :status)
          AND (:locationId IS NULL OR request.requesterAssignment.workShift.schedulePeriod.location.id = :locationId)
        ORDER BY request.createdAt DESC
        """)
    List<ShiftSwapRequest> findReviewDetailed(
        @Param("status") ShiftSwapStatus status,
        @Param("locationId") Long locationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
        "requesterUser", "requesterUser.role",
        "requesterUser.location", "requesterUser.position",
        "requesterAssignment", "requesterAssignment.position",
        "requesterAssignment.workShift",
        "requesterAssignment.workShift.shiftTemplate",
        "requesterAssignment.workShift.schedulePeriod",
        "requesterAssignment.workShift.schedulePeriod.location",
        "targetUser", "targetUser.role",
        "targetUser.location", "targetUser.position",
        "targetAssignment", "targetAssignment.position",
        "targetAssignment.workShift",
        "targetAssignment.workShift.shiftTemplate",
        "targetAssignment.workShift.schedulePeriod",
        "targetAssignment.workShift.schedulePeriod.location",
        "approvedBy"
    })
    @Query("SELECT request FROM ShiftSwapRequest request WHERE request.id = :id")
    Optional<ShiftSwapRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT request.requesterAssignment.workShift.schedulePeriod.id
        FROM ShiftSwapRequest request
        WHERE request.id = :id
        """)
    Optional<Long> findSchedulePeriodIdById(@Param("id") Long id);

    @Query("""
        SELECT COUNT(request)
        FROM ShiftSwapRequest request
        WHERE request.status IN :statuses
          AND (
              request.requesterAssignment.id = :assignmentId
              OR request.targetAssignment.id = :assignmentId
          )
        """)
    long countActiveByAssignmentId(
        @Param("assignmentId") Long assignmentId,
        @Param("statuses") Collection<ShiftSwapStatus> statuses
    );
}
