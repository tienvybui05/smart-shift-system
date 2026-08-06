package com.smartshift.repository;

import com.smartshift.entity.ShiftSwapRequest;
import com.smartshift.enums.ShiftSwapStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShiftSwapRequestRepository extends JpaRepository<ShiftSwapRequest, Long> {

    List<ShiftSwapRequest> findAllByStatusOrderByCreatedAtAsc(ShiftSwapStatus status);

    List<ShiftSwapRequest> findAllByTargetUserIdOrderByCreatedAtDesc(Long targetUserId);
}

