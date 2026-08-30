package com.smartshift.service;

import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.WorkShift;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScheduleAuditSnapshots {

    private ScheduleAuditSnapshots() {
    }

    public static Map<String, Object> schedulePeriod(
        SchedulePeriod schedulePeriod
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", schedulePeriod.getId());
        snapshot.put("name", schedulePeriod.getName());
        snapshot.put("locationId", schedulePeriod.getLocation().getId());
        snapshot.put("locationName", schedulePeriod.getLocation().getName());
        snapshot.put("startDate", schedulePeriod.getStartDate());
        snapshot.put("endDate", schedulePeriod.getEndDate());
        snapshot.put("status", schedulePeriod.getStatus());
        snapshot.put("publishedAt", schedulePeriod.getPublishedAt());
        return snapshot;
    }

    public static Map<String, Object> workShift(WorkShift workShift) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", workShift.getId());
        snapshot.put("schedulePeriodId", workShift.getSchedulePeriod().getId());
        snapshot.put(
            "shiftTemplateId",
            workShift.getShiftTemplate() == null
                ? null
                : workShift.getShiftTemplate().getId()
        );
        snapshot.put(
            "shiftTemplateName",
            workShift.getShiftTemplate() == null
                ? "Ca tùy chỉnh"
                : workShift.getShiftTemplate().getName()
        );
        snapshot.put("startAt", workShift.getStartAt());
        snapshot.put("endAt", workShift.getEndAt());
        snapshot.put("breakMinutes", workShift.getBreakMinutes());
        snapshot.put("status", workShift.getStatus());
        snapshot.put("note", workShift.getNote());
        return snapshot;
    }

    public static Map<String, Object> assignment(
        ShiftAssignment assignment
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", assignment.getId());
        snapshot.put("workShiftId", assignment.getWorkShift().getId());
        snapshot.put("userId", assignment.getUser().getId());
        snapshot.put("employeeCode", assignment.getUser().getEmployeeCode());
        snapshot.put("employeeName", assignment.getUser().getFullName());
        snapshot.put("positionId", assignment.getPosition().getId());
        snapshot.put("positionName", assignment.getPosition().getName());
        snapshot.put("source", assignment.getAssignmentSource());
        snapshot.put("status", assignment.getStatus());
        snapshot.put("note", assignment.getNote());
        return snapshot;
    }

    public static List<Map<String, Object>> requirements(
        Collection<ShiftRequirement> requirements
    ) {
        return requirements.stream()
            .map(ScheduleAuditSnapshots::requirement)
            .toList();
    }

    private static Map<String, Object> requirement(
        ShiftRequirement requirement
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", requirement.getId());
        snapshot.put("positionId", requirement.getPosition().getId());
        snapshot.put("positionName", requirement.getPosition().getName());
        snapshot.put("minEmployees", requirement.getMinEmployees());
        snapshot.put("maxEmployees", requirement.getMaxEmployees());
        snapshot.put("priority", requirement.getPriority());
        return snapshot;
    }
}
