package com.smartshift.repository;

import com.smartshift.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmployeeCode(String employeeCode);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmail(String email);

    List<User> findAllByLocationIdAndActiveTrue(Long locationId);
}