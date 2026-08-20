package com.smartshift.service;

import com.smartshift.dto.timeoff.TimeOffRequest;
import com.smartshift.dto.timeoff.TimeOffResponse;
import com.smartshift.dto.timeoff.TimeOffReviewRequest;
import com.smartshift.enums.TimeOffStatus;

import java.util.List;

public interface TimeOffService {

    List<TimeOffResponse> getMyRequests(String username);

    TimeOffResponse createMyRequest(
        String username,
        TimeOffRequest request
    );

    TimeOffResponse cancelMyRequest(Long id, String username);

    List<TimeOffResponse> getRequests(
        TimeOffStatus status,
        Long locationId,
        String currentUsername
    );

    TimeOffResponse reviewRequest(
        Long id,
        TimeOffReviewRequest request,
        String currentUsername
    );
}
