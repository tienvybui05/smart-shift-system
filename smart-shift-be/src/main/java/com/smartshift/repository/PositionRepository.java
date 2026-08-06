package com.smartshift.repository;

import com.smartshift.entity.Position;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PositionRepository extends JpaRepository<Position, Long> {

    Optional<Position> findByCode(String code);

    boolean existsByCode(String code);

    List<Position> findAllByActiveTrue();
}