package com.smartshift.service;

import com.smartshift.dto.autoschedule.AutoScheduleRequest;
import com.smartshift.dto.autoschedule.AutoScheduleResponse;

public interface AutoScheduleService {

    AutoScheduleResponse generate(
        AutoScheduleRequest request,
        String generatedByUsername
    );
}
