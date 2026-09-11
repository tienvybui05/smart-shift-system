package com.smartshift.service.impl;

import com.smartshift.dto.dashboard.DashboardResponse;
import com.smartshift.dto.dashboard.DashboardShiftResponse;
import com.smartshift.dto.dashboard.EmployeeDashboardSummary;
import com.smartshift.dto.dashboard.ManagementDashboardSummary;
import com.smartshift.entity.Attendance;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AttendanceStatus;
import com.smartshift.enums.OpenShiftClaimStatus;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.ShiftSwapStatus;
import com.smartshift.enums.TimeOffStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.repository.AttendanceRepository;
import com.smartshift.repository.NotificationRepository;
import com.smartshift.repository.OpenShiftClaimRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.ShiftSwapRequestRepository;
import com.smartshift.repository.TimeOffRequestRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.DashboardService;
import com.smartshift.service.OpenShiftClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String MANAGER_ROLE = "ROLE_MANAGER";
    private static final String EMPLOYEE_ROLE = "ROLE_EMPLOYEE";

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);
    private static final List<SchedulePeriodStatus> VISIBLE_PERIOD_STATUSES =
        List.of(SchedulePeriodStatus.PUBLISHED, SchedulePeriodStatus.LOCKED);
    private static final List<ShiftSwapStatus> ACTIVE_SWAP_STATUSES =
        List.of(ShiftSwapStatus.PENDING, ShiftSwapStatus.ACCEPTED);

    private final UserRepository userRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final TimeOffRequestRepository timeOffRequestRepository;
    private final OpenShiftClaimRepository openShiftClaimRepository;
    private final ShiftSwapRequestRepository shiftSwapRequestRepository;
    private final NotificationRepository notificationRepository;
    private final OpenShiftClaimService openShiftClaimService;
    private final Clock clock;

    @Override
    public DashboardResponse getDashboard(String username) {
        User user = userRepository.findDetailedByUsername(username)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy người dùng hiện tại"
            ));
        String role = user.getRole().getName();
        ZoneId zoneId = ZoneId.of(user.getLocation().getTimezone());
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zoneId);
        LocalDate weekStart = today.with(
            TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate weekEnd = weekStart.plusDays(6);

        if (EMPLOYEE_ROLE.equals(role)) {
            return buildEmployeeDashboard(
                user,
                role,
                zoneId,
                now,
                today,
                weekStart,
                weekEnd
            );
        }
        if (ADMIN_ROLE.equals(role) || MANAGER_ROLE.equals(role)) {
            return buildManagementDashboard(
                user,
                role,
                zoneId,
                now,
                today,
                weekStart,
                weekEnd
            );
        }
        throw new BusinessRuleException("Vai trò hiện tại chưa có dashboard");
    }

    private DashboardResponse buildEmployeeDashboard(
        User user,
        String role,
        ZoneId zoneId,
        Instant now,
        LocalDate today,
        LocalDate weekStart,
        LocalDate weekEnd
    ) {
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.with(
            TemporalAdjusters.lastDayOfMonth()
        );
        LocalDate assignmentStart = earlier(monthStart, weekStart);
        LocalDate assignmentEnd = later(monthEnd, today.plusDays(30));

        List<ShiftAssignment> assignments = shiftAssignmentRepository
            .findMyScheduleInRange(
                user.getUsername(),
                ACTIVE_ASSIGNMENT_STATUSES,
                startOfDay(assignmentStart, zoneId),
                startOfDay(assignmentEnd.plusDays(1), zoneId)
            ).stream()
            .filter(this::isVisibleAssignment)
            .toList();

        List<ShiftAssignment> weekAssignments = assignments.stream()
            .filter(assignment -> isWithin(
                localDate(assignment.getWorkShift()),
                weekStart,
                weekEnd
            ))
            .toList();
        long scheduledMinutes = weekAssignments.stream()
            .mapToLong(assignment -> scheduledMinutes(
                assignment.getWorkShift()
            ))
            .sum();

        List<Attendance> monthAttendances = attendanceRepository
            .findMyAttendancesInRange(
                user.getUsername(),
                startOfDay(monthStart, zoneId),
                startOfDay(monthEnd.plusDays(1), zoneId)
            );
        long workedMinutes = monthAttendances.stream()
            .mapToLong(this::workedMinutes)
            .sum();
        int onTimeRate = calculateOnTimeRate(monthAttendances);
        long pendingRequests = countEmployeePendingRequests(
            user.getUsername()
        );
        long availableOpenShifts = openShiftClaimService
            .getAvailableShifts(user.getUsername())
            .size();
        long unreadNotifications = notificationRepository
            .countByUserUsernameAndReadAtIsNull(user.getUsername());
        BigDecimal estimatedPay = moneyForMinutes(
            workedMinutes,
            user.getBasePayAmount(),
            user.getSalaryCoefficient()
        );

        List<DashboardShiftResponse> upcomingShifts = assignments.stream()
            .filter(assignment -> assignment.getWorkShift().getEndAt()
                .isAfter(now))
            .sorted(Comparator.comparing(
                assignment -> assignment.getWorkShift().getStartAt()
            ))
            .limit(5)
            .map(this::toEmployeeShift)
            .toList();

        EmployeeDashboardSummary summary = new EmployeeDashboardSummary(
            weekAssignments.size(),
            scheduledMinutes,
            workedMinutes,
            onTimeRate,
            pendingRequests,
            availableOpenShifts,
            unreadNotifications,
            estimatedPay
        );
        return new DashboardResponse(
            role,
            user.getLocation().getName(),
            now,
            today,
            weekStart,
            weekEnd,
            summary,
            null,
            upcomingShifts
        );
    }

    private DashboardResponse buildManagementDashboard(
        User user,
        String role,
        ZoneId zoneId,
        Instant now,
        LocalDate today,
        LocalDate weekStart,
        LocalDate weekEnd
    ) {
        Long locationId = ADMIN_ROLE.equals(role)
            ? null
            : user.getLocation().getId();
        String scopeName = locationId == null
            ? "Toàn hệ thống"
            : user.getLocation().getName();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.with(
            TemporalAdjusters.lastDayOfMonth()
        );
        LocalDate rangeStartDate = earlier(monthStart, weekStart);
        LocalDate rangeEndDate = later(monthEnd, today.plusDays(7));

        List<WorkShift> shifts = workShiftRepository.findDashboardShifts(
            locationId,
            VISIBLE_PERIOD_STATUSES,
            startOfDay(rangeStartDate, zoneId),
            startOfDay(rangeEndDate.plusDays(1), zoneId)
        );
        List<Long> shiftIds = shifts.stream().map(WorkShift::getId).toList();
        Map<Long, List<ShiftRequirement>> requirementsByShift =
            groupRequirements(shiftIds);
        Map<Long, List<ShiftAssignment>> assignmentsByShift =
            groupAssignments(shiftIds);

        List<WorkShift> todayShifts = shifts.stream()
            .filter(shift -> localDate(shift).equals(today))
            .toList();
        long understaffedToday = todayShifts.stream()
            .filter(shift -> coverage(
                shift,
                requirementsByShift,
                assignmentsByShift
            ).understaffed())
            .count();

        List<WorkShift> weekShifts = shifts.stream()
            .filter(shift -> isWithin(
                localDate(shift),
                weekStart,
                weekEnd
            ))
            .toList();
        Set<Long> weekShiftIds = toIdSet(weekShifts);
        long scheduledMinutesThisWeek = assignmentsByShift.entrySet()
            .stream()
            .filter(entry -> weekShiftIds.contains(entry.getKey()))
            .flatMap(entry -> entry.getValue().stream())
            .mapToLong(assignment -> scheduledMinutes(
                assignment.getWorkShift()
            ))
            .sum();

        List<Attendance> attendances = attendanceRepository.search(
            locationId,
            null,
            startOfDay(rangeStartDate, zoneId),
            startOfDay(monthEnd.plusDays(1), zoneId)
        );
        List<Attendance> todayAttendances = attendances.stream()
            .filter(attendance -> attendanceDate(attendance).equals(today))
            .toList();
        List<Attendance> weekAttendances = attendances.stream()
            .filter(attendance -> isWithin(
                attendanceDate(attendance),
                weekStart,
                weekEnd
            ))
            .toList();
        List<Attendance> monthAttendances = attendances.stream()
            .filter(attendance -> isWithin(
                attendanceDate(attendance),
                monthStart,
                monthEnd
            ))
            .toList();

        long checkedInToday = todayAttendances.stream()
            .filter(attendance -> attendance.getCheckInAt() != null)
            .count();
        long lateToday = todayAttendances.stream()
            .filter(attendance -> attendance.getCheckInAt() != null)
            .filter(attendance -> attendance.getLateMinutes() > 0)
            .count();
        long absentToday = todayAttendances.stream()
            .filter(attendance -> attendance.getStatus()
                == AttendanceStatus.ABSENT)
            .count();
        long missingCheckInsToday = countMissingCheckIns(
            todayShifts,
            assignmentsByShift,
            todayAttendances,
            now
        );
        long workedMinutesThisWeek = weekAttendances.stream()
            .mapToLong(this::workedMinutes)
            .sum();
        BigDecimal estimatedLaborCost = monthAttendances.stream()
            .map(this::attendanceCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        long activeEmployees = userRepository
            .countEmployeesByScopeAndStatus(locationId, true);
        long inactiveEmployees = userRepository
            .countEmployeesByScopeAndStatus(locationId, false);
        long pendingTimeOff = timeOffRequestRepository
            .countByStatusAndScope(TimeOffStatus.PENDING, locationId);
        long pendingShiftSwaps = shiftSwapRequestRepository
            .countByStatusAndScope(ShiftSwapStatus.ACCEPTED, locationId);
        long pendingOpenShiftClaims = openShiftClaimRepository
            .countByStatusAndScope(OpenShiftClaimStatus.PENDING, locationId);

        List<DashboardShiftResponse> upcomingShifts = shifts.stream()
            .filter(shift -> shift.getEndAt().isAfter(now))
            .filter(shift -> !localDate(shift).isAfter(today.plusDays(7)))
            .sorted(Comparator.comparing(WorkShift::getStartAt))
            .limit(8)
            .map(shift -> toManagementShift(
                shift,
                requirementsByShift,
                assignmentsByShift
            ))
            .toList();

        ManagementDashboardSummary summary =
            new ManagementDashboardSummary(
                activeEmployees,
                inactiveEmployees,
                todayShifts.size(),
                todayShifts.size() - understaffedToday,
                understaffedToday,
                checkedInToday,
                lateToday,
                absentToday,
                missingCheckInsToday,
                pendingTimeOff,
                pendingShiftSwaps,
                pendingOpenShiftClaims,
                scheduledMinutesThisWeek,
                workedMinutesThisWeek,
                estimatedLaborCost
            );
        return new DashboardResponse(
            role,
            scopeName,
            now,
            today,
            weekStart,
            weekEnd,
            null,
            summary,
            upcomingShifts
        );
    }

    private long countEmployeePendingRequests(String username) {
        return timeOffRequestRepository.countByUserUsernameAndStatus(
            username,
            TimeOffStatus.PENDING
        ) + openShiftClaimRepository.countByUserUsernameAndStatus(
            username,
            OpenShiftClaimStatus.PENDING
        ) + shiftSwapRequestRepository.countMineByStatuses(
            username,
            ACTIVE_SWAP_STATUSES
        );
    }

    private Map<Long, List<ShiftRequirement>> groupRequirements(
        List<Long> shiftIds
    ) {
        Map<Long, List<ShiftRequirement>> grouped = new HashMap<>();
        if (shiftIds.isEmpty()) {
            return grouped;
        }
        shiftRequirementRepository.findAllByWorkShiftIds(shiftIds)
            .forEach(requirement -> grouped.computeIfAbsent(
                requirement.getWorkShift().getId(),
                ignored -> new java.util.ArrayList<>()
            ).add(requirement));
        return grouped;
    }

    private Map<Long, List<ShiftAssignment>> groupAssignments(
        List<Long> shiftIds
    ) {
        Map<Long, List<ShiftAssignment>> grouped = new HashMap<>();
        if (shiftIds.isEmpty()) {
            return grouped;
        }
        shiftAssignmentRepository.findAllByWorkShiftIdsAndStatusIn(
            shiftIds,
            ACTIVE_ASSIGNMENT_STATUSES
        ).forEach(assignment -> grouped.computeIfAbsent(
            assignment.getWorkShift().getId(),
            ignored -> new java.util.ArrayList<>()
        ).add(assignment));
        return grouped;
    }

    private Coverage coverage(
        WorkShift shift,
        Map<Long, List<ShiftRequirement>> requirementsByShift,
        Map<Long, List<ShiftAssignment>> assignmentsByShift
    ) {
        List<ShiftRequirement> requirements = requirementsByShift.getOrDefault(
            shift.getId(),
            List.of()
        );
        List<ShiftAssignment> assignments = assignmentsByShift.getOrDefault(
            shift.getId(),
            List.of()
        );
        long required = requirements.stream()
            .mapToLong(ShiftRequirement::getMinEmployees)
            .sum();
        boolean understaffed = requirements.stream().anyMatch(requirement -> {
            long assignedForPosition = assignments.stream()
                .filter(assignment -> assignment.getPosition().getId()
                    .equals(requirement.getPosition().getId()))
                .count();
            return assignedForPosition < requirement.getMinEmployees();
        });
        return new Coverage(assignments.size(), required, understaffed);
    }

    private DashboardShiftResponse toManagementShift(
        WorkShift shift,
        Map<Long, List<ShiftRequirement>> requirementsByShift,
        Map<Long, List<ShiftAssignment>> assignmentsByShift
    ) {
        Coverage coverage = coverage(
            shift,
            requirementsByShift,
            assignmentsByShift
        );
        return new DashboardShiftResponse(
            shift.getId(),
            null,
            shiftName(shift),
            colorCode(shift),
            shift.getSchedulePeriod().getLocation().getName(),
            null,
            shift.getStartAt(),
            shift.getEndAt(),
            shift.getBreakMinutes(),
            shift.getStatus(),
            shift.getSchedulePeriod().getStatus(),
            coverage.assigned(),
            coverage.required(),
            coverage.understaffed()
        );
    }

    private DashboardShiftResponse toEmployeeShift(
        ShiftAssignment assignment
    ) {
        WorkShift shift = assignment.getWorkShift();
        return new DashboardShiftResponse(
            shift.getId(),
            assignment.getId(),
            shiftName(shift),
            colorCode(shift),
            shift.getSchedulePeriod().getLocation().getName(),
            assignment.getPosition().getName(),
            shift.getStartAt(),
            shift.getEndAt(),
            shift.getBreakMinutes(),
            shift.getStatus(),
            shift.getSchedulePeriod().getStatus(),
            1,
            0,
            false
        );
    }

    private long countMissingCheckIns(
        List<WorkShift> todayShifts,
        Map<Long, List<ShiftAssignment>> assignmentsByShift,
        List<Attendance> todayAttendances,
        Instant now
    ) {
        Set<Long> recordedAssignmentIds = new HashSet<>();
        todayAttendances.forEach(attendance -> recordedAssignmentIds.add(
            attendance.getShiftAssignment().getId()
        ));
        return todayShifts.stream()
            .filter(shift -> !shift.getStartAt().isAfter(now))
            .flatMap(shift -> assignmentsByShift.getOrDefault(
                shift.getId(),
                List.of()
            ).stream())
            .filter(assignment -> !recordedAssignmentIds.contains(
                assignment.getId()
            ))
            .count();
    }

    private int calculateOnTimeRate(List<Attendance> attendances) {
        long checkedIn = attendances.stream()
            .filter(attendance -> attendance.getCheckInAt() != null)
            .count();
        if (checkedIn == 0) {
            return 0;
        }
        long onTime = attendances.stream()
            .filter(attendance -> attendance.getCheckInAt() != null)
            .filter(attendance -> attendance.getLateMinutes() == 0)
            .count();
        return (int) Math.round(onTime * 100.0 / checkedIn);
    }

    private long workedMinutes(Attendance attendance) {
        if (attendance.getCheckInAt() == null
            || attendance.getCheckOutAt() == null) {
            return 0;
        }
        long elapsed = Duration.between(
            attendance.getCheckInAt(),
            attendance.getCheckOutAt()
        ).toMinutes();
        return Math.max(0, elapsed - attendance.getBreakMinutes());
    }

    private long scheduledMinutes(WorkShift shift) {
        long elapsed = Duration.between(
            shift.getStartAt(),
            shift.getEndAt()
        ).toMinutes();
        return Math.max(0, elapsed - shift.getBreakMinutes());
    }

    private BigDecimal attendanceCost(Attendance attendance) {
        User employee = attendance.getShiftAssignment().getUser();
        return moneyForMinutes(
            workedMinutes(attendance),
            employee.getBasePayAmount(),
            employee.getSalaryCoefficient()
        );
    }

    private BigDecimal moneyForMinutes(
        long minutes,
        BigDecimal basePayAmount,
        BigDecimal coefficient
    ) {
        if (minutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(minutes)
            .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP)
            .multiply(basePayAmount)
            .multiply(coefficient)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isVisibleAssignment(ShiftAssignment assignment) {
        WorkShift shift = assignment.getWorkShift();
        return VISIBLE_PERIOD_STATUSES.contains(
            shift.getSchedulePeriod().getStatus()
        ) && shift.getStatus() != WorkShiftStatus.CANCELLED;
    }

    private String shiftName(WorkShift shift) {
        return shift.getShiftTemplate() == null
            ? "Ca tùy chỉnh"
            : shift.getShiftTemplate().getName();
    }

    private String colorCode(WorkShift shift) {
        return shift.getShiftTemplate() == null
            ? null
            : shift.getShiftTemplate().getColorCode();
    }

    private LocalDate attendanceDate(Attendance attendance) {
        return localDate(attendance.getShiftAssignment().getWorkShift());
    }

    private LocalDate localDate(WorkShift shift) {
        ZoneId zoneId = ZoneId.of(
            shift.getSchedulePeriod().getLocation().getTimezone()
        );
        return LocalDate.ofInstant(shift.getStartAt(), zoneId);
    }

    private Instant startOfDay(LocalDate date, ZoneId zoneId) {
        return date.atStartOfDay(zoneId).toInstant();
    }

    private boolean isWithin(
        LocalDate value,
        LocalDate start,
        LocalDate end
    ) {
        return !value.isBefore(start) && !value.isAfter(end);
    }

    private LocalDate earlier(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }

    private LocalDate later(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private Set<Long> toIdSet(Collection<WorkShift> shifts) {
        Set<Long> ids = new HashSet<>();
        shifts.forEach(shift -> ids.add(shift.getId()));
        return ids;
    }

    private record Coverage(
        long assigned,
        long required,
        boolean understaffed
    ) {
    }
}
