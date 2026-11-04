package com.smartshift.repository;

import com.smartshift.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select l from Location l where l.id=:id")
    Optional<Location> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Location> findByCode(String code);

    boolean existsByCode(String code);

    List<Location> findAllByActiveTrue();
}
