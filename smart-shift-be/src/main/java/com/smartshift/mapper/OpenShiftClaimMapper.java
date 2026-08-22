package com.smartshift.mapper;

import com.smartshift.dto.openshift.OpenShiftClaimResponse;
import com.smartshift.entity.OpenShiftClaim;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.OpenShiftClaimStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class OpenShiftClaimMapper {

    public OpenShiftClaim toEntity(
        WorkShift workShift,
        User employee,
        String reason
    ) {
        OpenShiftClaim claim = new OpenShiftClaim();
        claim.setWorkShift(workShift);
        claim.setUser(employee);
        claim.setReason(normalizeNullableText(reason));
        claim.setStatus(OpenShiftClaimStatus.PENDING);
        return claim;
    }

    public OpenShiftClaimResponse toResponse(OpenShiftClaim claim) {
        WorkShift workShift = claim.getWorkShift();
        SchedulePeriod period = workShift.getSchedulePeriod();
        User employee = claim.getUser();
        User reviewedBy = claim.getReviewedBy();
        ZoneId zoneId = ZoneId.of(period.getLocation().getTimezone());
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);
        return new OpenShiftClaimResponse(
            claim.getId(),
            workShift.getId(),
            workShift.getShiftTemplate() == null
                ? "Ca tùy chỉnh"
                : workShift.getShiftTemplate().getName(),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            localEnd.toLocalDate().isAfter(localStart.toLocalDate()),
            period.getId(),
            period.getName(),
            period.getStatus(),
            period.getLocation().getId(),
            period.getLocation().getName(),
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            employee.getPosition().getId(),
            employee.getPosition().getName(),
            claim.getStatus(),
            claim.getReason(),
            reviewedBy == null ? null : reviewedBy.getId(),
            reviewedBy == null ? null : reviewedBy.getFullName(),
            claim.getReviewedAt(),
            claim.getReviewerNote(),
            claim.getAssignment() == null
                ? null
                : claim.getAssignment().getId(),
            claim.getCreatedAt(),
            claim.getStatus() == OpenShiftClaimStatus.PENDING
                && workShift.getStartAt().isAfter(Instant.now())
        );
    }

    public String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
