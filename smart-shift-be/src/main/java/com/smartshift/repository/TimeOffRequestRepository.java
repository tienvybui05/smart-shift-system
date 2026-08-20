package com.smartshift.repository;

import com.smartshift.entity.TimeOffRequest;
import com.smartshift.enums.TimeOffStatus;
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

public interface TimeOffRequestRepository extends JpaRepository<TimeOffRequest, Long> {

    @EntityGraph(attributePaths = {"user", "user.location", "approvedBy"})
    List<TimeOffRequest> findAllByUserUsernameOrderByCreatedAtDesc(
        String username
    );

    @EntityGraph(attributePaths = {"user", "user.location", "approvedBy"})
    @Query("""
        SELECT request
        FROM TimeOffRequest request
        WHERE (:status IS NULL OR request.status = :status)
          AND (:locationId IS NULL OR request.user.location.id = :locationId)
        ORDER BY request.createdAt DESC
        """)
    List<TimeOffRequest> search(
        @Param("status") TimeOffStatus status,
        @Param("locationId") Long locationId
    );

    @Query("""
        SELECT CASE WHEN COUNT(request) > 0 THEN true ELSE false END
        FROM TimeOffRequest request
        WHERE request.user.id = :userId
          AND request.status IN :statuses
          AND request.startAt < :rangeEnd
          AND request.endAt > :rangeStart
        """)
    boolean existsOverlappingRequest(
        @Param("userId") Long userId,
        @Param("statuses") Collection<TimeOffStatus> statuses,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT request
        FROM TimeOffRequest request
        JOIN FETCH request.user user
        JOIN FETCH user.location location
        LEFT JOIN FETCH request.approvedBy approvedBy
        WHERE request.id = :id
        """)
    Optional<TimeOffRequest> findByIdForUpdate(@Param("id") Long id);
}
