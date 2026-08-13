package com.smartshift.service;

import com.smartshift.dto.workshift.GenerateWorkShiftsRequest;
import com.smartshift.dto.workshift.GenerateWorkShiftsResponse;
import com.smartshift.dto.workshift.WorkShiftRequest;
import com.smartshift.dto.workshift.WorkShiftResponse;
import com.smartshift.enums.WorkShiftStatus;

import java.util.List;

public interface WorkShiftService {

    List<WorkShiftResponse> getWorkShifts(
        Long schedulePeriodId,
        WorkShiftStatus status
    );

    WorkShiftResponse getWorkShiftById(Long id);

    WorkShiftResponse createWorkShift(WorkShiftRequest request);

    WorkShiftResponse updateWorkShift(Long id, WorkShiftRequest request);

    GenerateWorkShiftsResponse generateWorkShifts(
        GenerateWorkShiftsRequest request
    );

    WorkShiftResponse updateStatus(Long id, WorkShiftStatus status);
}
