package com.smartshift.service;

import com.smartshift.dto.assignment.AssignmentCandidateResponse;
import com.smartshift.dto.assignment.MyWorkScheduleResponse;
import com.smartshift.dto.assignment.ShiftAssignmentRequest;
import com.smartshift.dto.assignment.ShiftAssignmentSummaryResponse;

import java.util.List;
import java.time.LocalDate;

public interface ShiftAssignmentService {

    ShiftAssignmentSummaryResponse getSummary(Long workShiftId);

    List<AssignmentCandidateResponse> getCandidates(
        Long workShiftId,
        Long positionId
    );

    List<MyWorkScheduleResponse> getMySchedule(
        String username,
        LocalDate startDate,
        LocalDate endDate
    );

    ShiftAssignmentSummaryResponse assignEmployee(
        Long workShiftId,
        ShiftAssignmentRequest request,
        String assignedByUsername
    );

    ShiftAssignmentSummaryResponse removeAssignment(Long assignmentId);
}
