package com.smartshift.repository;

import com.smartshift.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByShiftAssignmentId(Long shiftAssignmentId);
}

