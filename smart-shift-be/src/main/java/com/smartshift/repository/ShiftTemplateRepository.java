package com.smartshift.repository;

import com.smartshift.entity.ShiftTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, Long> {

    List<ShiftTemplate> findAllByLocationIdAndActiveTrueOrderByStartTime(Long locationId);

    Optional<ShiftTemplate> findByLocationIdAndName(Long locationId, String name);
}

