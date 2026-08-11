package com.smartshift.repository;

import com.smartshift.entity.ShiftTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, Long> {

    @Query("""
        SELECT shiftTemplate
        FROM ShiftTemplate shiftTemplate
        JOIN FETCH shiftTemplate.location location
        WHERE (:locationId IS NULL OR location.id = :locationId)
          AND (:active IS NULL OR shiftTemplate.active = :active)
        ORDER BY location.name ASC, shiftTemplate.startTime ASC
        """)
    List<ShiftTemplate> search(
        @Param("locationId") Long locationId,
        @Param("active") Boolean active
    );

    boolean existsByLocationIdAndNameIgnoreCase(Long locationId, String name);

    boolean existsByLocationIdAndNameIgnoreCaseAndIdNot(
        Long locationId,
        String name,
        Long id
    );
}
