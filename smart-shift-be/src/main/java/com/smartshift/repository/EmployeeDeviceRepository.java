package com.smartshift.repository;

import com.smartshift.entity.EmployeeDevice;
import com.smartshift.enums.DeviceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmployeeDeviceRepository extends JpaRepository<EmployeeDevice, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT device
        FROM EmployeeDevice device
        JOIN FETCH device.user employee
        WHERE employee.id = :userId
          AND device.deviceFingerprint = :fingerprint
        """)
    Optional<EmployeeDevice> findByUserIdAndFingerprintForUpdate(
        @Param("userId") Long userId,
        @Param("fingerprint") String fingerprint
    );

    @Query("""
        SELECT device
        FROM EmployeeDevice device
        JOIN FETCH device.user employee
        LEFT JOIN FETCH device.approvedBy approver
        WHERE employee.id = :userId
        ORDER BY device.registeredAt DESC
        """)
    List<EmployeeDevice> findAllByUserId(@Param("userId") Long userId);

    @Query("""
        SELECT device
        FROM EmployeeDevice device
        JOIN FETCH device.user employee
        JOIN FETCH employee.location location
        LEFT JOIN FETCH device.approvedBy approver
        WHERE location.id = :locationId
          AND device.status = :status
        ORDER BY device.registeredAt DESC
        """)
    List<EmployeeDevice> findAllByLocationIdAndStatus(
        @Param("locationId") Long locationId,
        @Param("status") DeviceStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT device
        FROM EmployeeDevice device
        JOIN FETCH device.user employee
        JOIN FETCH employee.location location
        LEFT JOIN FETCH device.approvedBy approver
        WHERE device.id = :id
        """)
    Optional<EmployeeDevice> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUserIdAndStatus(Long userId, DeviceStatus status);
}
