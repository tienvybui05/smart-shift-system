package com.smartshift.service;

import com.smartshift.dto.availability.AvailabilityRequest;
import com.smartshift.dto.availability.AvailabilityResponse;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeAvailabilityService {

    List<AvailabilityResponse> getMyAvailabilities(
        String username,
        LocalDate startDate,
        LocalDate endDate
    );

    List<AvailabilityResponse> getUserAvailabilities(
        Long userId,
        LocalDate startDate,
        LocalDate endDate
    );

    AvailabilityResponse createMyAvailability(
        String username,
        AvailabilityRequest request
    );

    AvailabilityResponse updateMyAvailability(
        Long id,
        String username,
        AvailabilityRequest request
    );

    void deleteMyAvailability(Long id, String username);
}
