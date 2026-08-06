package com.smartshift.repository;

import com.smartshift.entity.ShiftRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftRequirementRepository extends JpaRepository<ShiftRequirement, Long> {

    List<ShiftRequirement> findAllByWorkShiftId(Long workShiftId);

    Optional<ShiftRequirement> findByWorkShiftIdAndPositionId(Long workShiftId, Long positionId);
}

