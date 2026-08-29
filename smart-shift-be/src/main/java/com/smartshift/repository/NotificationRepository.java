package com.smartshift.repository;

import com.smartshift.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
        SELECT notification
        FROM Notification notification
        WHERE notification.user.username = :username
          AND (:unreadOnly = false OR notification.readAt IS NULL)
        ORDER BY notification.createdAt DESC
        """)
    Page<Notification> searchByUser(
        @Param("username") String username,
        @Param("unreadOnly") boolean unreadOnly,
        Pageable pageable
    );

    long countByUserUsernameAndReadAtIsNull(String username);

    Optional<Notification> findByIdAndUserUsername(
        Long id,
        String username
    );

    @Modifying
    @Query("""
        UPDATE Notification notification
        SET notification.readAt = :readAt
        WHERE notification.user.username = :username
          AND notification.readAt IS NULL
        """)
    int markAllAsRead(
        @Param("username") String username,
        @Param("readAt") Instant readAt
    );
}
