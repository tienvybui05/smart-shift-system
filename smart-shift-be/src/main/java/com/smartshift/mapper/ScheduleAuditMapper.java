package com.smartshift.mapper;

import com.smartshift.dto.audit.ScheduleAuditResponse;
import com.smartshift.entity.ScheduleAuditLog;
import org.springframework.stereotype.Component;

@Component
public class ScheduleAuditMapper {

    public ScheduleAuditResponse toResponse(ScheduleAuditLog audit) {
        return new ScheduleAuditResponse(
            audit.getId(),
            audit.getLocation().getId(),
            audit.getLocation().getName(),
            audit.getSchedulePeriod().getId(),
            audit.getSchedulePeriod().getName(),
            audit.getWorkShiftId(),
            audit.getAction(),
            audit.getTargetType(),
            audit.getTargetId(),
            audit.getActor().getId(),
            audit.getActorName(),
            audit.getReason(),
            audit.getBeforeData(),
            audit.getAfterData(),
            audit.getCreatedAt()
        );
    }
}
