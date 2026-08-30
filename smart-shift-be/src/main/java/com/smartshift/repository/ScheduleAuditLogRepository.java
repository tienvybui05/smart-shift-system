package com.smartshift.repository;

import com.smartshift.entity.ScheduleAuditLog;
import com.smartshift.enums.ScheduleAuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ScheduleAuditLogRepository
    extends JpaRepository<ScheduleAuditLog, Long> {

    @Query("""
        SELECT audit
        FROM ScheduleAuditLog audit
        JOIN FETCH audit.location location
        JOIN FETCH audit.schedulePeriod schedulePeriod
        JOIN FETCH audit.actor actor
        WHERE (:locationId IS NULL OR location.id = :locationId)
          AND (:schedulePeriodId IS NULL OR schedulePeriod.id = :schedulePeriodId)
          AND (:action IS NULL OR audit.action = :action)
          AND audit.createdAt >= :rangeStart
          AND audit.createdAt < :rangeEnd
        ORDER BY audit.createdAt DESC, audit.id DESC
        """)
    List<ScheduleAuditLog> search(
        @Param("locationId") Long locationId,
        @Param("schedulePeriodId") Long schedulePeriodId,
        @Param("action") ScheduleAuditAction action,
        @Param("rangeStart") Instant rangeStart,
        @Param("rangeEnd") Instant rangeEnd
    );
}
