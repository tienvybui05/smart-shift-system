package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.entity.EmployeeAvailability;
import com.smartshift.entity.Position;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.repository.EmployeeAvailabilityRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.TimeOffRequestRepository;
import com.smartshift.service.AssignmentConstraintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentConstraintServiceImpl
    implements AssignmentConstraintService {

    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

    private static final List<AssignmentStatus> ACTIVE_STATUSES = List.of(
        AssignmentStatus.ASSIGNED,
        AssignmentStatus.CONFIRMED
    );

    private static final LocalTime END_OF_DAY_INPUT = LocalTime.of(23, 59);

    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeAvailabilityRepository availabilityRepository;
    private final TimeOffRequestRepository timeOffRequestRepository;

    @Override
    public AssignmentConstraintResult evaluate(
        User employee,
        WorkShift workShift,
        Position requiredPosition
    ) {
        List<String> violations = new ArrayList<>();
        validateEmployee(employee, workShift, requiredPosition, violations);

        ZoneId zoneId = resolveZoneId(workShift, violations);
        if (zoneId != null) {
            validateShiftInsidePeriod(workShift, zoneId, violations);
        }

        if (timeOffRequestRepository.existsOverlappingRequest(
            employee.getId(),
            List.of(TimeOffStatus.APPROVED),
            workShift.getStartAt(),
            workShift.getEndAt()
        )) {
            violations.add(
                "Nhân viên có đơn nghỉ đã được duyệt trong thời gian của ca"
            );
        }

        AvailabilityType availabilityType = null;
        if (zoneId != null) {
            availabilityType = findAvailabilityCoverage(
                employee,
                workShift,
                zoneId
            );
            if (availabilityType == null) {
                violations.add(
                    "Chưa đăng ký rảnh cho toàn bộ thời gian của ca"
                );
            }
        }

        List<ShiftAssignment> existingAssignments = zoneId == null
            ? List.of()
            : loadAssignmentsForRules(employee, workShift, zoneId).stream()
                .filter(assignment -> assignment.getWorkShift().getStatus()
                    != WorkShiftStatus.CANCELLED)
                .filter(assignment -> !assignment.getWorkShift().getId().equals(
                    workShift.getId()
                ))
                .toList();

        validateTimeConflict(existingAssignments, workShift, violations);
        validateMinimumRest(
            employee,
            existingAssignments,
            workShift,
            violations
        );

        BigDecimal targetHours = calculateNetHours(workShift);
        BigDecimal projectedDailyHours = targetHours;
        BigDecimal projectedWeeklyHours = targetHours;
        if (zoneId != null) {
            projectedDailyHours = calculateAssignedHours(
                existingAssignments,
                workShift,
                zoneId,
                true
            ).add(targetHours);
            projectedWeeklyHours = calculateAssignedHours(
                existingAssignments,
                workShift,
                zoneId,
                false
            ).add(targetHours);
        }

        if (projectedDailyHours.compareTo(employee.getMaxHoursPerDay()) > 0) {
            violations.add(
                "Vượt giới hạn " + employee.getMaxHoursPerDay()
                    + " giờ làm trong ngày"
            );
        }
        if (projectedWeeklyHours.compareTo(employee.getMaxHoursPerWeek()) > 0) {
            violations.add(
                "Vượt giới hạn " + employee.getMaxHoursPerWeek()
                    + " giờ làm trong tuần"
            );
        }

        return new AssignmentConstraintResult(
            availabilityType,
            violations,
            projectedDailyHours,
            projectedWeeklyHours
        );
    }

    private void validateEmployee(
        User employee,
        WorkShift workShift,
        Position requiredPosition,
        List<String> violations
    ) {
        if (!employee.isActive()) {
            violations.add("Nhân viên đang ngừng hoạt động");
        }

        if (employee.getRole() == null
            || !EMPLOYEE_ROLE.equals(employee.getRole().getName())) {
            violations.add("Tài khoản không thuộc vai trò nhân viên");
        }

        if (!workShift.getSchedulePeriod().getLocation().isActive()) {
            violations.add("Chi nhánh của ca đang ngừng hoạt động");
        }

        Long shiftLocationId = workShift.getSchedulePeriod()
            .getLocation()
            .getId();
        if (employee.getLocation() == null
            || !employee.getLocation().getId().equals(shiftLocationId)) {
            violations.add("Nhân viên không thuộc chi nhánh của ca làm");
        }

        if (requiredPosition == null || !requiredPosition.isActive()) {
            violations.add("Vị trí cần xếp đang ngừng hoạt động");
        } else if (employee.getPosition() == null
            || !employee.getPosition().getId().equals(requiredPosition.getId())) {
            violations.add("Nhân viên không còn phù hợp với vị trí cần xếp");
        }
    }

    private ZoneId resolveZoneId(
        WorkShift workShift,
        List<String> violations
    ) {
        try {
            return ZoneId.of(
                workShift.getSchedulePeriod().getLocation().getTimezone()
            );
        } catch (DateTimeException exception) {
            violations.add("Múi giờ của chi nhánh không hợp lệ");
            return null;
        }
    }

    private void validateShiftInsidePeriod(
        WorkShift workShift,
        ZoneId zoneId,
        List<String> violations
    ) {
        SchedulePeriod schedulePeriod = workShift.getSchedulePeriod();
        if (!workShift.getEndAt().isAfter(workShift.getStartAt())) {
            violations.add("Thời gian kết thúc ca phải sau thời gian bắt đầu");
            return;
        }

        long durationMinutes = Duration.between(
            workShift.getStartAt(),
            workShift.getEndAt()
        ).toMinutes();
        if (workShift.getBreakMinutes() < 0
            || workShift.getBreakMinutes() >= durationMinutes) {
            violations.add("Thời gian nghỉ giữa ca không hợp lệ");
        }

        LocalDate workDate = workShift.getStartAt()
            .atZone(zoneId)
            .toLocalDate();
        if (workDate.isBefore(schedulePeriod.getStartDate())
            || workDate.isAfter(schedulePeriod.getEndDate())) {
            violations.add("Ca làm không nằm trong khoảng ngày của kỳ lịch");
        }
    }

    private AvailabilityType findAvailabilityCoverage(
        User employee,
        WorkShift workShift,
        ZoneId zoneId
    ) {
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);
        List<EmployeeAvailability> availabilities = availabilityRepository
            .findAllByUserIdAndAvailableDateBetweenOrderByAvailableDateAscStartTimeAsc(
                employee.getId(),
                localStart.toLocalDate(),
                localEnd.toLocalDate()
            );

        List<AvailabilityWindow> windows = availabilities.stream()
            .map(availability -> toWindow(availability, zoneId))
            .toList();
        boolean hasUnavailableOverlap = windows.stream()
            .filter(window -> window.type() == AvailabilityType.UNAVAILABLE)
            .anyMatch(window -> overlaps(
                window.startAt(),
                window.endAt(),
                workShift.getStartAt(),
                workShift.getEndAt()
            ));
        if (hasUnavailableOverlap) {
            return null;
        }

        List<AvailabilityWindow> positiveWindows = windows.stream()
            .filter(window -> window.type() != AvailabilityType.UNAVAILABLE)
            .filter(window -> overlaps(
                window.startAt(),
                window.endAt(),
                workShift.getStartAt(),
                workShift.getEndAt()
            ))
            .sorted(Comparator.comparing(AvailabilityWindow::startAt))
            .toList();

        Instant coveredUntil = workShift.getStartAt();
        boolean allPreferred = true;
        for (AvailabilityWindow window : positiveWindows) {
            if (window.startAt().isAfter(coveredUntil)) {
                return null;
            }
            if (window.endAt().isAfter(coveredUntil)) {
                coveredUntil = window.endAt();
                allPreferred &= window.type() == AvailabilityType.PREFERRED;
            }
            if (!coveredUntil.isBefore(workShift.getEndAt())) {
                return allPreferred
                    ? AvailabilityType.PREFERRED
                    : AvailabilityType.AVAILABLE;
            }
        }
        return null;
    }

    private AvailabilityWindow toWindow(
        EmployeeAvailability availability,
        ZoneId zoneId
    ) {
        LocalDate date = availability.getAvailableDate();
        LocalDateTime start = date.atTime(availability.getStartTime());
        LocalDateTime end = availability.getEndTime().equals(END_OF_DAY_INPUT)
            ? date.plusDays(1).atStartOfDay()
            : date.atTime(availability.getEndTime());
        return new AvailabilityWindow(
            start.atZone(zoneId).toInstant(),
            end.atZone(zoneId).toInstant(),
            availability.getAvailabilityType()
        );
    }

    private boolean overlaps(
        Instant firstStart,
        Instant firstEnd,
        Instant secondStart,
        Instant secondEnd
    ) {
        return firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart);
    }

    private List<ShiftAssignment> loadAssignmentsForRules(
        User employee,
        WorkShift targetShift,
        ZoneId zoneId
    ) {
        LocalDate targetDate = targetShift.getStartAt()
            .atZone(zoneId)
            .toLocalDate();
        LocalDate weekStart = targetDate.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        long restMinutes = employee.getMinRestHours()
            .multiply(BigDecimal.valueOf(60))
            .longValue();
        Instant rangeStart = weekStart.atStartOfDay(zoneId)
            .minusMinutes(restMinutes)
            .toInstant();
        Instant rangeEnd = weekStart.plusWeeks(1)
            .atStartOfDay(zoneId)
            .plusMinutes(restMinutes)
            .toInstant();
        return shiftAssignmentRepository.findActiveAssignmentsInRange(
            employee.getId(),
            ACTIVE_STATUSES,
            rangeStart,
            rangeEnd
        );
    }

    private void validateTimeConflict(
        List<ShiftAssignment> assignments,
        WorkShift targetShift,
        List<String> violations
    ) {
        boolean conflicting = assignments.stream().anyMatch(assignment ->
            overlaps(
                assignment.getWorkShift().getStartAt(),
                assignment.getWorkShift().getEndAt(),
                targetShift.getStartAt(),
                targetShift.getEndAt()
            )
        );
        if (conflicting) {
            violations.add("Bị trùng thời gian với một ca đã được phân công");
        }
    }

    private void validateMinimumRest(
        User employee,
        List<ShiftAssignment> assignments,
        WorkShift targetShift,
        List<String> violations
    ) {
        BigDecimal minimumRest = employee.getMinRestHours();
        boolean insufficientRest = assignments.stream().anyMatch(assignment -> {
            WorkShift existingShift = assignment.getWorkShift();
            if (!existingShift.getEndAt().isAfter(targetShift.getStartAt())) {
                return durationHours(
                    existingShift.getEndAt(),
                    targetShift.getStartAt()
                ).compareTo(minimumRest) < 0;
            }
            if (!targetShift.getEndAt().isAfter(existingShift.getStartAt())) {
                return durationHours(
                    targetShift.getEndAt(),
                    existingShift.getStartAt()
                ).compareTo(minimumRest) < 0;
            }
            return false;
        });
        if (insufficientRest) {
            violations.add(
                "Không đủ tối thiểu " + minimumRest
                    + " giờ nghỉ giữa hai ca"
            );
        }
    }

    private BigDecimal calculateAssignedHours(
        List<ShiftAssignment> assignments,
        WorkShift targetShift,
        ZoneId zoneId,
        boolean sameDayOnly
    ) {
        LocalDate targetDate = targetShift.getStartAt()
            .atZone(zoneId)
            .toLocalDate();
        LocalDate weekStart = targetDate.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate weekEnd = weekStart.plusWeeks(1);

        return assignments.stream()
            .filter(assignment -> {
                LocalDate assignmentDate = assignment.getWorkShift()
                    .getStartAt()
                    .atZone(zoneId)
                    .toLocalDate();
                if (sameDayOnly) {
                    return assignmentDate.equals(targetDate);
                }
                return !assignmentDate.isBefore(weekStart)
                    && assignmentDate.isBefore(weekEnd);
            })
            .map(assignment -> calculateNetHours(assignment.getWorkShift()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateNetHours(WorkShift workShift) {
        long durationMinutes = Duration.between(
            workShift.getStartAt(),
            workShift.getEndAt()
        ).toMinutes();
        long netMinutes = durationMinutes - workShift.getBreakMinutes();
        return BigDecimal.valueOf(netMinutes)
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal durationHours(Instant start, Instant end) {
        return BigDecimal.valueOf(Duration.between(start, end).toMinutes())
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private record AvailabilityWindow(
        Instant startAt,
        Instant endAt,
        AvailabilityType type
    ) {
    }
}
