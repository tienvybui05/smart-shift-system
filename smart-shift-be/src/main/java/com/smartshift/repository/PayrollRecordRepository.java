package com.smartshift.repository;

import com.smartshift.entity.PayrollRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRecordRepository
    extends JpaRepository<PayrollRecord, Long> {

    @Query("""
        SELECT record
        FROM PayrollRecord record
        JOIN FETCH record.user employee
        JOIN FETCH employee.position position
        JOIN FETCH record.location location
        JOIN FETCH record.calculatedBy calculatedBy
        LEFT JOIN FETCH record.confirmedBy confirmedBy
        WHERE (:locationId IS NULL OR location.id = :locationId)
          AND record.periodStart = :startDate
          AND record.periodEnd = :endDate
        ORDER BY employee.fullName ASC
        """)
    List<PayrollRecord> findDetailedByPeriod(
        @Param("locationId") Long locationId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    @Query("""
        SELECT record
        FROM PayrollRecord record
        JOIN FETCH record.user employee
        JOIN FETCH employee.position position
        JOIN FETCH record.location location
        JOIN FETCH record.calculatedBy calculatedBy
        LEFT JOIN FETCH record.confirmedBy confirmedBy
        WHERE employee.username = :username
          AND record.periodStart >= :startDate
          AND record.periodEnd <= :endDate
        ORDER BY record.periodEnd DESC, record.periodStart DESC
        """)
    List<PayrollRecord> findMyRecordsInRange(
        @Param("username") String username,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    Optional<PayrollRecord> findByUserIdAndPeriodStartAndPeriodEnd(
        Long userId,
        LocalDate periodStart,
        LocalDate periodEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT record
        FROM PayrollRecord record
        JOIN FETCH record.user employee
        JOIN FETCH employee.position position
        JOIN FETCH record.location location
        JOIN FETCH record.calculatedBy calculatedBy
        LEFT JOIN FETCH record.confirmedBy confirmedBy
        WHERE record.id = :id
        """)
    Optional<PayrollRecord> findDetailedByIdForUpdate(@Param("id") Long id);
}
