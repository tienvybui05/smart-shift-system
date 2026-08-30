package com.smartshift.repository;

import com.smartshift.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"role", "location", "position"})
    @Query("SELECT user FROM User user WHERE user.username = :username")
    Optional<User> findByUsernameForUpdate(
        @Param("username") String username
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"role", "location", "position"})
    @Query("SELECT user FROM User user WHERE user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    Optional<User> findByEmployeeCode(String employeeCode);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndActiveTrue(String username);

    List<User> findAllByLocationIdAndActiveTrue(Long locationId);

    @EntityGraph(attributePaths = {"role", "location"})
    @Query("""
        SELECT user
        FROM User user
        WHERE user.active = true
          AND (
              user.role.name = 'ROLE_ADMIN'
              OR (
                  user.role.name = 'ROLE_MANAGER'
                  AND user.location.id = :locationId
              )
          )
        ORDER BY user.id ASC
        """)
    List<User> findActiveNotificationReviewersForLocation(
        @Param("locationId") Long locationId
    );

    @EntityGraph(attributePaths = {"role", "location", "position"})
    @Query("""
        SELECT user
        FROM User user
        WHERE user.location.id = :locationId
          AND user.position.id = :positionId
          AND user.active = true
          AND user.role.name = 'ROLE_EMPLOYEE'
        ORDER BY user.fullName ASC
        """)
    List<User> findSchedulableByLocationAndPosition(
        @Param("locationId") Long locationId,
        @Param("positionId") Long positionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"role", "location", "position"})
    @Query("""
        SELECT user
        FROM User user
        WHERE user.location.id = :locationId
          AND user.active = true
          AND user.role.name = 'ROLE_EMPLOYEE'
        ORDER BY user.id ASC
        """)
    List<User> findAllSchedulableByLocationForUpdate(
        @Param("locationId") Long locationId
    );

    @EntityGraph(attributePaths = {"role", "location", "position"})
    @Query("""
        SELECT user
        FROM User user
        WHERE user.location.id = :locationId
          AND user.active = true
          AND user.role.name = 'ROLE_EMPLOYEE'
        ORDER BY user.fullName ASC
        """)
    List<User> findActivePayrollEmployeesByLocation(
        @Param("locationId") Long locationId
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
