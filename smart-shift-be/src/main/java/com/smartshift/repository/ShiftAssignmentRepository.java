package com.smartshift.repository;

import com.smartshift.entity.ShiftAssignment;
import com.smartshift.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    List<ShiftAssignment> findAllByWorkShiftId(Long workShiftId);

    List<ShiftAssignment> findAllByUserIdAndStatus(Long userId, AssignmentStatus status);

    Optional<ShiftAssignment> findByWorkShiftIdAndUserId(Long workShiftId, Long userId);
}

