package com.smartshift.repository;

import com.smartshift.entity.OpenShiftClaim;
import com.smartshift.enums.OpenShiftClaimStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OpenShiftClaimRepository
    extends JpaRepository<OpenShiftClaim, Long> {

    @EntityGraph(attributePaths = {
        "workShift",
        "workShift.schedulePeriod",
        "workShift.schedulePeriod.location",
        "workShift.shiftTemplate",
        "user",
        "user.location",
        "user.position",
        "reviewedBy",
        "assignment"
    })
    List<OpenShiftClaim> findAllByUserUsernameOrderByCreatedAtDesc(
        String username
    );

    @EntityGraph(attributePaths = {
        "workShift",
        "workShift.schedulePeriod",
        "workShift.schedulePeriod.location",
        "workShift.shiftTemplate",
        "user",
        "user.location",
        "user.position",
        "reviewedBy",
        "assignment"
    })
    @Query("""
        SELECT claim
        FROM OpenShiftClaim claim
        WHERE (:status IS NULL OR claim.status = :status)
          AND (:locationId IS NULL
            OR claim.workShift.schedulePeriod.location.id = :locationId)
        ORDER BY claim.createdAt DESC
        """)
    List<OpenShiftClaim> search(
        @Param("status") OpenShiftClaimStatus status,
        @Param("locationId") Long locationId
    );

    boolean existsByWorkShiftIdAndUserIdAndStatus(
        Long workShiftId,
        Long userId,
        OpenShiftClaimStatus status
    );

    long countByWorkShiftIdAndStatus(
        Long workShiftId,
        OpenShiftClaimStatus status
    );

    Optional<OpenShiftClaim> findFirstByWorkShiftIdAndUserIdAndStatus(
        Long workShiftId,
        Long userId,
        OpenShiftClaimStatus status
    );

    @Query("""
        SELECT claim.workShift.schedulePeriod.id
        FROM OpenShiftClaim claim
        WHERE claim.id = :id
        """)
    Optional<Long> findSchedulePeriodIdByClaimId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
        "workShift",
        "workShift.schedulePeriod",
        "workShift.schedulePeriod.location",
        "workShift.shiftTemplate",
        "user",
        "user.role",
        "user.location",
        "user.position",
        "reviewedBy",
        "assignment"
    })
    @Query("SELECT claim FROM OpenShiftClaim claim WHERE claim.id = :id")
    Optional<OpenShiftClaim> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "user.position"})
    @Query("""
        SELECT claim
        FROM OpenShiftClaim claim
        WHERE claim.workShift.id = :workShiftId
          AND claim.user.position.id = :positionId
          AND claim.status = :status
        ORDER BY claim.id ASC
        """)
    List<OpenShiftClaim> findAllByShiftPositionAndStatusForUpdate(
        @Param("workShiftId") Long workShiftId,
        @Param("positionId") Long positionId,
        @Param("status") OpenShiftClaimStatus status
    );
}
