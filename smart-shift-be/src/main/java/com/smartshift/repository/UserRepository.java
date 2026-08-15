package com.smartshift.repository;

import com.smartshift.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmployeeCode(String employeeCode);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndActiveTrue(String username);

    List<User> findAllByLocationIdAndActiveTrue(Long locationId);

    @EntityGraph(attributePaths = {"role", "location", "position"})
    List<User> findAllByLocationIdAndPositionIdAndActiveTrueOrderByFullNameAsc(
        Long locationId,
        Long positionId
    );

    @EntityGraph(attributePaths = {"role", "location", "position"})
    @Query("""
        SELECT user
        FROM User user
        WHERE (
            LOWER(user.employeeCode) LIKE CONCAT('%', LOWER(:keyword), '%')
            OR LOWER(user.username) LIKE CONCAT('%', LOWER(:keyword), '%')
            OR LOWER(user.fullName) LIKE CONCAT('%', LOWER(:keyword), '%')
            OR LOWER(COALESCE(user.email, '')) LIKE CONCAT('%', LOWER(:keyword), '%')
        )
        AND (:locationId IS NULL OR user.location.id = :locationId)
        AND (:positionId IS NULL OR user.position.id = :positionId)
        AND (:active IS NULL OR user.active = :active)
        """)
    Page<User> search(
        @Param("keyword") String keyword,
        @Param("locationId") Long locationId,
        @Param("positionId") Long positionId,
        @Param("active") Boolean active,
        Pageable pageable
    );
}
