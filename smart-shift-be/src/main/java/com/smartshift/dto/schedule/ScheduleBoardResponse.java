package com.smartshift.dto.schedule;

import com.smartshift.dto.assignment.AssignmentRequirementProgressResponse;
import com.smartshift.dto.workshift.WorkShiftResponse;
import com.smartshift.enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record ScheduleBoardResponse(Long periodId,String periodName,SchedulePeriodStatus periodStatus,
    LocalDate startDate,LocalDate endDate,Long locationId,String locationName,String timezone,
    Instant generatedAt,List<Card> shifts,List<Employee> employees) {
    public record Card(WorkShiftResponse shift,boolean editable,
        List<AssignmentRequirementProgressResponse> requirements,List<Assignment> assignments) {}
    public record Assignment(Long id,Long userId,String employeeCode,String fullName,Long positionId,
        String positionName,AssignmentSource source,long version,boolean editable) {}
    public record Employee(Long id,String employeeCode,String fullName,Long positionId,String positionName,
        BigDecimal maxHoursPerDay,BigDecimal maxHoursPerWeek,BigDecimal minRestHours) {}
}
