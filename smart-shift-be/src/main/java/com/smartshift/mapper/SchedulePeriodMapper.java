package com.smartshift.mapper;

import com.smartshift.dto.schedule.SchedulePeriodRequest;
import com.smartshift.dto.schedule.SchedulePeriodResponse;
import com.smartshift.entity.Location;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.User;
import com.smartshift.enums.SchedulePeriodStatus;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;

@Component
public class SchedulePeriodMapper {

    public SchedulePeriod toEntity(
        SchedulePeriodRequest request,
        Location location,
        User createdBy
    ) {
        SchedulePeriod schedulePeriod = new SchedulePeriod();
        schedulePeriod.setCreatedBy(createdBy);
        schedulePeriod.setStatus(SchedulePeriodStatus.DRAFT);
        updateEntity(request, schedulePeriod, location);
        return schedulePeriod;
    }

    public void updateEntity(
        SchedulePeriodRequest request,
        SchedulePeriod schedulePeriod,
        Location location
    ) {
        schedulePeriod.setLocation(location);
        schedulePeriod.setName(request.name().trim());
        schedulePeriod.setStartDate(request.startDate());
        schedulePeriod.setEndDate(request.endDate());
    }

    public SchedulePeriodResponse toResponse(SchedulePeriod schedulePeriod) {
        User publishedBy = schedulePeriod.getPublishedBy();
        long totalDays = ChronoUnit.DAYS.between(
            schedulePeriod.getStartDate(),
            schedulePeriod.getEndDate()
        ) + 1;

        return new SchedulePeriodResponse(
            schedulePeriod.getId(),
            schedulePeriod.getLocation().getId(),
            schedulePeriod.getLocation().getCode(),
            schedulePeriod.getLocation().getName(),
            schedulePeriod.getName(),
            schedulePeriod.getStartDate(),
            schedulePeriod.getEndDate(),
            totalDays,
            schedulePeriod.getStatus(),
            schedulePeriod.getStatus() == SchedulePeriodStatus.DRAFT,
            schedulePeriod.getCreatedBy().getId(),
            schedulePeriod.getCreatedBy().getFullName(),
            publishedBy == null ? null : publishedBy.getId(),
            publishedBy == null ? null : publishedBy.getFullName(),
            schedulePeriod.getPublishedAt(),
            schedulePeriod.getCreatedAt(),
            schedulePeriod.getUpdatedAt()
        );
    }
}
