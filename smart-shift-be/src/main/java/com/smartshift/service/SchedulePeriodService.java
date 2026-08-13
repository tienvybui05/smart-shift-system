package com.smartshift.service;

import com.smartshift.dto.schedule.SchedulePeriodRequest;
import com.smartshift.dto.schedule.SchedulePeriodResponse;
import com.smartshift.enums.SchedulePeriodStatus;

import java.util.List;

public interface SchedulePeriodService {

    List<SchedulePeriodResponse> getSchedulePeriods(
        Long locationId,
        SchedulePeriodStatus status
    );

    SchedulePeriodResponse getSchedulePeriodById(Long id);

    SchedulePeriodResponse createSchedulePeriod(
        SchedulePeriodRequest request,
        String currentUsername
    );

    SchedulePeriodResponse updateSchedulePeriod(
        Long id,
        SchedulePeriodRequest request
    );
}
