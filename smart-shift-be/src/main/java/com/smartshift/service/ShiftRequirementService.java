package com.smartshift.service;

import com.smartshift.dto.requirement.SaveShiftRequirementsRequest;
import com.smartshift.dto.requirement.ShiftRequirementSummaryResponse;

public interface ShiftRequirementService {

    ShiftRequirementSummaryResponse getRequirements(Long workShiftId);

    ShiftRequirementSummaryResponse saveRequirements(
        Long workShiftId,
        SaveShiftRequirementsRequest request
    );
}
