package com.smartshift.repository;

import com.smartshift.entity.TimeOffRequest;
import com.smartshift.enums.TimeOffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimeOffRequestRepository extends JpaRepository<TimeOffRequest, Long> {

    List<TimeOffRequest> findAllByUserIdOrderByStartAtDesc(Long userId);

    List<TimeOffRequest> findAllByStatusOrderByCreatedAtAsc(TimeOffStatus status);
}

