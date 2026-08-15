package com.smartshift.dto.assignment;

public record AssignmentRequirementProgressResponse(
    Long requirementId,
    Long positionId,
    String positionCode,
    String positionName,
    short minEmployees,
    short maxEmployees,
    int assignedEmployees,
    boolean minimumMet,
    boolean maximumReached
) {
}
