package com.smartshift.repository;

import com.smartshift.entity.ShiftRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;

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

    @EntityGraph(attributePaths = {"workShift", "position"})
    @Query("""
        SELECT requirement
        FROM ShiftRequirement requirement
        WHERE requirement.workShift.id IN :workShiftIds
        ORDER BY requirement.workShift.startAt ASC,
          requirement.priority ASC,
          requirement.position.name ASC
        """)
    List<ShiftRequirement> findAllByWorkShiftIds(
        @Param("workShiftIds") Collection<Long> workShiftIds
    );

    @EntityGraph(attributePaths = {"position"})
    Optional<ShiftRequirement> findByWorkShiftIdAndPositionId(
        Long workShiftId,
        Long positionId
    );
}
