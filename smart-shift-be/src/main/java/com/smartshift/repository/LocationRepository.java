package com.smartshift.repository;

import com.smartshift.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    Optional<Location> findByCode(String code);

    boolean existsByCode(String code);

    List<Location> findAllByActiveTrue();
}