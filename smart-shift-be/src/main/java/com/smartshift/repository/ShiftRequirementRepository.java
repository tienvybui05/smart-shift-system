package com.smartshift.repository;

import com.smartshift.entity.ShiftRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShiftRequirementRepository extends JpaRepository<ShiftRequirement, Long> {

    @Query("""
        SELECT requirement
        FROM ShiftRequirement requirement
        JOIN FETCH requirement.workShift workShift
        JOIN FETCH requirement.position position
        WHERE workShift.id = :workShiftId
        ORDER BY requirement.priority ASC, position.name ASC
        """)
    List<ShiftRequirement> findAllByWorkShiftId(
        @Param("workShiftId") Long workShiftId
    );
}
