package com.smartshift.repository;
import com.smartshift.entity.AttendanceCorrection;
import com.smartshift.enums.AttendanceCorrectionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrection, Long> {
    @Query("select c.shiftAssignment.id from AttendanceCorrection c where c.id=:id")
    Optional<Long> findAssignmentId(@Param("id") Long id);

    boolean existsByShiftAssignmentIdAndStatus(Long assignmentId, AttendanceCorrectionStatus status);

    @EntityGraph(attributePaths={"shiftAssignment","shiftAssignment.workShift","shiftAssignment.workShift.schedulePeriod",
        "shiftAssignment.workShift.schedulePeriod.location","requester","reviewedBy"})
    @Query("""
        select c from AttendanceCorrection c
        where (:username is null or c.requester.username=:username)
          and (:locationId is null or c.shiftAssignment.workShift.schedulePeriod.location.id=:locationId)
          and (:status is null or c.status=:status)
          and c.shiftAssignment.workShift.startAt >= :from
          and c.shiftAssignment.workShift.startAt < :to
        order by c.createdAt desc, c.id desc
        """)
    List<AttendanceCorrection> search(@Param("username") String username, @Param("locationId") Long locationId,
        @Param("status") AttendanceCorrectionStatus status, @Param("from") Instant from, @Param("to") Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AttendanceCorrection c where c.id=:id")
    Optional<AttendanceCorrection> findForUpdate(@Param("id") Long id);

    @Query("""
        select count(c) > 0 from AttendanceCorrection c where c.requester.id=:userId
          and c.status=com.smartshift.enums.AttendanceCorrectionStatus.PENDING
          and c.shiftAssignment.workShift.startAt < :to and c.shiftAssignment.workShift.endAt > :from
        """)
    boolean hasPendingForPayroll(@Param("userId") Long userId, @Param("from") Instant from, @Param("to") Instant to);
}

