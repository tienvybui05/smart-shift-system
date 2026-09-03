package com.smartshift.service;

import com.smartshift.dto.shiftswap.ShiftSwapCandidateResponse;
import com.smartshift.dto.shiftswap.ShiftSwapCreateRequest;
import com.smartshift.dto.shiftswap.ShiftSwapRequestResponse;
import com.smartshift.dto.shiftswap.ShiftSwapRespondRequest;
import com.smartshift.dto.shiftswap.ShiftSwapReviewRequest;
import com.smartshift.enums.ShiftSwapStatus;

import java.util.List;

public interface ShiftSwapService {

    List<ShiftSwapRequestResponse> getMyRequests(String username);

    List<ShiftSwapRequestResponse> getAvailableGiveaways(String username);

    List<ShiftSwapCandidateResponse> getSwapCandidates(
        String username,
        Long requesterAssignmentId
    );

    ShiftSwapRequestResponse createRequest(
        String username,
        ShiftSwapCreateRequest request
    );

    ShiftSwapRequestResponse respondToRequest(
        String username,
        Long id,
        ShiftSwapRespondRequest request
    );

    ShiftSwapRequestResponse cancelRequest(String username, Long id);

    List<ShiftSwapRequestResponse> getRequestsForReview(
        String username,
        ShiftSwapStatus status,
        Long locationId
    );

    ShiftSwapRequestResponse reviewRequest(
        String username,
        Long id,
        ShiftSwapReviewRequest request
    );
}
