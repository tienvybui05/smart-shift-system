package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.autoschedule.AutoScheduleAssignmentResponse;
import com.smartshift.dto.autoschedule.AutoScheduleRequest;
import com.smartshift.dto.autoschedule.AutoScheduleResponse;
import com.smartshift.dto.autoschedule.AutoScheduleShortageResponse;
import com.smartshift.entity.SchedulePeriod;
import com.smartshift.entity.ShiftAssignment;
import com.smartshift.entity.ShiftRequirement;
import com.smartshift.entity.User;
import com.smartshift.entity.WorkShift;
import com.smartshift.enums.AssignmentStatus;
import com.smartshift.enums.AvailabilityType;
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.WorkShiftStatus;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.exception.ResourceNotFoundException;
import com.smartshift.mapper.ShiftAssignmentMapper;
import com.smartshift.repository.SchedulePeriodRepository;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.repository.ShiftRequirementRepository;
import com.smartshift.repository.UserRepository;
import com.smartshift.repository.WorkShiftRepository;
import com.smartshift.service.AssignmentConstraintService;
import com.smartshift.service.AutoScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoScheduleServiceImpl implements AutoScheduleService {

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

    private static final BigDecimal PREFERRED_SCORE = new BigDecimal("100");
    private static final BigDecimal AVAILABLE_SCORE = new BigDecimal("60");
    private static final BigDecimal MINIMUM_HOURS_WEIGHT = new BigDecimal("2");
    private static final BigDecimal MAXIMUM_DEFICIT_SCORE_HOURS =
        new BigDecimal("40");
    private static final BigDecimal FAIRNESS_WEIGHT = new BigDecimal("20");

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final UserRepository userRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final AssignmentConstraintService assignmentConstraintService;

    @Override
    @Transactional
    public AutoScheduleResponse generate(
        AutoScheduleRequest request,
        String generatedByUsername
    ) {
        Instant generatedAt = Instant.now();
        SchedulePeriod schedulePeriod = findSchedulePeriodForUpdate(
            request.schedulePeriodId()
        );
        validateSchedulable(schedulePeriod);

        User generatedBy = userRepository.findByUsername(generatedByUsername)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy tài khoản đang sinh lịch"
            ));
        List<WorkShift> activeShifts = workShiftRepository.search(
            schedulePeriod.getId(),
            null
        ).stream()
            .filter(this::isSchedulableStatus)
            .toList();
        if (activeShifts.isEmpty()) {
            throw new BusinessRuleException(
                "Kỳ xếp lịch không có ca đang mở để sinh lịch tự động"
            );
        }

        List<User> employees = userRepository
            .findAllSchedulableByLocationForUpdate(
                schedulePeriod.getLocation().getId()
            );
        Map<Long, List<User>> employeesByPosition = groupByPosition(employees);
        Map<Long, Set<Long>> assignedUserIdsByShift = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<RequirementTask> tasks = buildTasks(
            activeShifts,
            assignedUserIdsByShift,
            warnings
        );
        if (tasks.isEmpty()) {
            warnings.add(
                "Không có nhu cầu nhân sự nào để hệ thống tự động phân công"
            );
        }
        if (employees.isEmpty()) {
            warnings.add(
                "Chi nhánh chưa có tài khoản nhân viên đang hoạt động"
            );
        }

        for (RequirementTask task : tasks) {
            task.eligibleCandidates = countEligibleCandidates(
                task,
                employeesByPosition,
                assignedUserIdsByShift,
                generatedAt
            );
        }
        tasks.sort(requirementTaskComparator());

        int assignedBefore = tasks.stream()
            .mapToInt(task -> task.assignedEmployees)
            .sum();
        List<AutoScheduleAssignmentResponse> generatedAssignments =
            new ArrayList<>();
        int preferredAssignments = 0;

        for (RequirementTask task : tasks) {
            while (task.assignedEmployees < task.requirement.getMinEmployees()) {
                CandidateSearchResult searchResult = findCandidates(
                    task,
                    employeesByPosition,
                    assignedUserIdsByShift,
                    generatedAt
                );
                if (searchResult.eligibleCandidates().isEmpty()) {
                    task.failureReasons = searchResult.failureReasons();
                    break;
                }

                CandidateEvaluation selected = searchResult
                    .eligibleCandidates()
                    .get(0);
                ShiftAssignment assignment = shiftAssignmentMapper.toAutoEntity(
                    task.workShift,
                    selected.employee(),
                    generatedBy,
                    selected.score(),
                    String.join("; ", selected.selectionReasons())
                );
                ShiftAssignment saved = shiftAssignmentRepository.saveAndFlush(
                    assignment
                );

                task.assignedEmployees++;
                assignedUserIdsByShift
                    .computeIfAbsent(
                        task.workShift.getId(),
                        ignored -> new HashSet<>()
                    )
                    .add(selected.employee().getId());
                generatedAssignments.add(toAssignmentResponse(
                    saved,
                    selected.selectionReasons()
                ));
                if (selected.constraintResult().availabilityType()
                    == AvailabilityType.PREFERRED) {
                    preferredAssignments++;
                }
            }
        }

        refreshWorkShiftStatuses(activeShifts, tasks);
        List<AutoScheduleShortageResponse> shortages = tasks.stream()
            .filter(task -> task.assignedEmployees
                < task.requirement.getMinEmployees())
            .map(this::toShortageResponse)
            .toList();

        int totalRequired = tasks.stream()
            .mapToInt(task -> task.requirement.getMinEmployees())
            .sum();
        int assignedAfter = assignedBefore + generatedAssignments.size();
        int coveredRequired = tasks.stream()
            .mapToInt(task -> Math.min(
                task.assignedEmployees,
                task.requirement.getMinEmployees()
            ))
            .sum();
        int unfilledPositions = Math.max(totalRequired - coveredRequired, 0);
        int fullyStaffedShifts = countFullyStaffedShifts(activeShifts, tasks);
        int understaffedShifts = (int) shortages.stream()
            .map(AutoScheduleShortageResponse::workShiftId)
            .distinct()
            .count();
        BigDecimal coveragePercentage = percentage(
            coveredRequired,
            totalRequired
        );
        BigDecimal preferencePercentage = generatedAssignments.isEmpty()
            ? BigDecimal.ZERO.setScale(2)
            : percentage(preferredAssignments, generatedAssignments.size());
        BigDecimal qualityScore = generatedAssignments.isEmpty()
            ? coveragePercentage
            : coveragePercentage.multiply(new BigDecimal("0.80"))
                .add(preferencePercentage.multiply(new BigDecimal("0.20")))
                .setScale(2, RoundingMode.HALF_UP);

        return new AutoScheduleResponse(
            schedulePeriod.getId(),
            generatedAt,
            activeShifts.size(),
            tasks.size(),
            totalRequired,
            assignedBefore,
            generatedAssignments.size(),
            assignedAfter,
            fullyStaffedShifts,
            understaffedShifts,
            unfilledPositions,
            preferredAssignments,
            coveragePercentage,
            preferencePercentage,
            qualityScore,
            warnings,
            generatedAssignments,
            shortages
        );
    }

    private List<RequirementTask> buildTasks(
        List<WorkShift> workShifts,
        Map<Long, Set<Long>> assignedUserIdsByShift,
        List<String> warnings
    ) {
        List<RequirementTask> tasks = new ArrayList<>();
        for (WorkShift workShift : workShifts) {
            List<ShiftRequirement> requirements = shiftRequirementRepository
                .findAllByWorkShiftId(workShift.getId());
            List<ShiftAssignment> assignments = shiftAssignmentRepository
                .findAllDetailedByWorkShiftIdAndStatusIn(
                    workShift.getId(),
                    ACTIVE_ASSIGNMENT_STATUSES
                );
            assignedUserIdsByShift.put(
                workShift.getId(),
                assignments.stream()
                    .map(assignment -> assignment.getUser().getId())
                    .collect(java.util.stream.Collectors.toSet())
            );

            if (requirements.isEmpty()) {
                warnings.add(
                    shiftLabel(workShift)
                        + " chưa được khai báo nhu cầu nhân sự"
                );
                continue;
            }

            for (ShiftRequirement requirement : requirements) {
                int assigned = (int) assignments.stream()
                    .filter(assignment -> assignment.getPosition().getId()
                        .equals(requirement.getPosition().getId()))
                    .count();
                RequirementTask task = new RequirementTask(
                    workShift,
                    requirement,
                    assigned
                );
                if (assigned > requirement.getMaxEmployees()) {
                    task.failureReasons = List.of(
                        "Số phân công hiện có đang vượt mức tối đa của vị trí"
                    );
                }
                tasks.add(task);
            }
        }
        return tasks;
    }

    private Map<Long, List<User>> groupByPosition(List<User> employees) {
        Map<Long, List<User>> result = new HashMap<>();
        for (User employee : employees) {
            result.computeIfAbsent(
                employee.getPosition().getId(),
                ignored -> new ArrayList<>()
            ).add(employee);
        }
        return result;
    }

    private int countEligibleCandidates(
        RequirementTask task,
        Map<Long, List<User>> employeesByPosition,
        Map<Long, Set<Long>> assignedUserIdsByShift,
        Instant generatedAt
    ) {
        return findCandidates(
            task,
            employeesByPosition,
            assignedUserIdsByShift,
            generatedAt
        ).eligibleCandidates().size();
    }

    private CandidateSearchResult findCandidates(
        RequirementTask task,
        Map<Long, List<User>> employeesByPosition,
        Map<Long, Set<Long>> assignedUserIdsByShift,
        Instant generatedAt
    ) {
        if (!task.workShift.getStartAt().isAfter(generatedAt)) {
            return new CandidateSearchResult(
                List.of(),
                List.of("Ca đã bắt đầu hoặc đang diễn ra")
            );
        }

        List<User> employees = employeesByPosition.getOrDefault(
            task.requirement.getPosition().getId(),
            List.of()
        );
        if (employees.isEmpty()) {
            return new CandidateSearchResult(
                List.of(),
                List.of(
                    "Không có nhân viên đang hoạt động phù hợp với vị trí"
                )
            );
        }

        Set<Long> assignedUserIds = assignedUserIdsByShift.getOrDefault(
            task.workShift.getId(),
            Set.of()
        );
        List<CandidateEvaluation> eligibleCandidates = new ArrayList<>();
        Set<String> failureReasons = new LinkedHashSet<>();
        for (User employee : employees) {
            if (assignedUserIds.contains(employee.getId())) {
                failureReasons.add("Nhân viên phù hợp đã được xếp vào ca này");
                continue;
            }

            AssignmentConstraintResult constraintResult =
                assignmentConstraintService.evaluate(
                    employee,
                    task.workShift,
                    task.requirement.getPosition()
                );
            if (!constraintResult.eligible()) {
                failureReasons.addAll(constraintResult.violations());
                continue;
            }

            List<String> selectionReasons = buildSelectionReasons(
                employee,
                constraintResult,
                task.workShift
            );
            eligibleCandidates.add(new CandidateEvaluation(
                employee,
                constraintResult,
                calculateScore(employee, constraintResult, task.workShift),
                selectionReasons
            ));
        }
        eligibleCandidates.sort(candidateComparator());
        if (failureReasons.isEmpty() && eligibleCandidates.isEmpty()) {
            failureReasons.add("Không còn ứng viên hợp lệ cho vị trí");
        }
        return new CandidateSearchResult(
            List.copyOf(eligibleCandidates),
            List.copyOf(failureReasons).stream().limit(5).toList()
        );
    }

    private BigDecimal calculateScore(
        User employee,
        AssignmentConstraintResult constraintResult,
        WorkShift workShift
    ) {
        BigDecimal availabilityScore = constraintResult.availabilityType()
            == AvailabilityType.PREFERRED
            ? PREFERRED_SCORE
            : AVAILABLE_SCORE;
        BigDecimal targetHours = calculateNetHours(workShift);
        BigDecimal currentWeeklyHours = constraintResult.projectedWeeklyHours()
            .subtract(targetHours)
            .max(BigDecimal.ZERO);
        BigDecimal minimumHoursDeficit = employee.getMinHoursPerWeek()
            .subtract(currentWeeklyHours)
            .max(BigDecimal.ZERO)
            .min(MAXIMUM_DEFICIT_SCORE_HOURS);
        BigDecimal minimumHoursScore = minimumHoursDeficit.multiply(
            MINIMUM_HOURS_WEIGHT
        );

        BigDecimal fairnessScore = BigDecimal.ZERO;
        if (employee.getMaxHoursPerWeek().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainingCapacity = employee.getMaxHoursPerWeek()
                .subtract(constraintResult.projectedWeeklyHours())
                .max(BigDecimal.ZERO);
            fairnessScore = remainingCapacity
                .divide(
                    employee.getMaxHoursPerWeek(),
                    6,
                    RoundingMode.HALF_UP
                )
                .multiply(FAIRNESS_WEIGHT);
        }

        return availabilityScore
            .add(minimumHoursScore)
            .add(fairnessScore)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> buildSelectionReasons(
        User employee,
        AssignmentConstraintResult constraintResult,
        WorkShift workShift
    ) {
        List<String> reasons = new ArrayList<>();
        if (constraintResult.availabilityType() == AvailabilityType.PREFERRED) {
            reasons.add("Đăng ký ưu tiên cho toàn bộ thời gian ca");
        } else {
            reasons.add("Đăng ký rảnh cho toàn bộ thời gian ca");
        }

        BigDecimal currentWeeklyHours = constraintResult.projectedWeeklyHours()
            .subtract(calculateNetHours(workShift))
            .max(BigDecimal.ZERO);
        if (currentWeeklyHours.compareTo(employee.getMinHoursPerWeek()) < 0) {
            reasons.add(
                "Đang dưới định mức giờ tối thiểu trong tuần"
            );
        }
        reasons.add(
            "Tổng giờ dự kiến trong tuần: "
                + constraintResult.projectedWeeklyHours().toPlainString()
        );
        return List.copyOf(reasons);
    }

    private Comparator<RequirementTask> requirementTaskComparator() {
        return Comparator
            .comparingInt((RequirementTask task) ->
                task.requirement.getPriority()
            )
            .thenComparingInt(task -> task.eligibleCandidates)
            .thenComparing(task -> task.workShift.getStartAt())
            .thenComparing(task -> task.requirement.getPosition().getName());
    }

    private Comparator<CandidateEvaluation> candidateComparator() {
        return Comparator
            .comparing(
                CandidateEvaluation::score,
                Comparator.reverseOrder()
            )
            .thenComparing(candidate -> candidate.constraintResult()
                .projectedWeeklyHours())
            .thenComparing(candidate -> candidate.employee().getFullName())
            .thenComparing(candidate -> candidate.employee().getId());
    }

    private void refreshWorkShiftStatuses(
        List<WorkShift> workShifts,
        List<RequirementTask> tasks
    ) {
        Map<Long, List<RequirementTask>> tasksByShift = new HashMap<>();
        for (RequirementTask task : tasks) {
            tasksByShift.computeIfAbsent(
                task.workShift.getId(),
                ignored -> new ArrayList<>()
            ).add(task);
        }

        for (WorkShift workShift : workShifts) {
            List<RequirementTask> shiftTasks = tasksByShift.getOrDefault(
                workShift.getId(),
                List.of()
            );
            boolean fullyStaffed = !shiftTasks.isEmpty()
                && shiftTasks.stream().allMatch(task ->
                    task.assignedEmployees
                        >= task.requirement.getMinEmployees()
                );
            workShift.setStatus(
                fullyStaffed ? WorkShiftStatus.FILLED : WorkShiftStatus.OPEN
            );
        }
        workShiftRepository.saveAll(workShifts);
        workShiftRepository.flush();
    }

    private int countFullyStaffedShifts(
        List<WorkShift> workShifts,
        List<RequirementTask> tasks
    ) {
        Set<Long> shiftsWithRequirements = tasks.stream()
            .map(task -> task.workShift.getId())
            .collect(java.util.stream.Collectors.toSet());
        Set<Long> understaffedShiftIds = tasks.stream()
            .filter(task -> task.assignedEmployees
                < task.requirement.getMinEmployees())
            .map(task -> task.workShift.getId())
            .collect(java.util.stream.Collectors.toSet());
        return (int) workShifts.stream()
            .map(WorkShift::getId)
            .filter(shiftsWithRequirements::contains)
            .filter(id -> !understaffedShiftIds.contains(id))
            .count();
    }

    private AutoScheduleAssignmentResponse toAssignmentResponse(
        ShiftAssignment assignment,
        List<String> selectionReasons
    ) {
        WorkShift workShift = assignment.getWorkShift();
        ZoneId zoneId = ZoneId.of(
            workShift.getSchedulePeriod().getLocation().getTimezone()
        );
        ZonedDateTime localStart = workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = workShift.getEndAt().atZone(zoneId);
        User employee = assignment.getUser();
        return new AutoScheduleAssignmentResponse(
            assignment.getId(),
            workShift.getId(),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            localEnd.toLocalDate().isAfter(localStart.toLocalDate()),
            employee.getId(),
            employee.getEmployeeCode(),
            employee.getFullName(),
            assignment.getPosition().getId(),
            assignment.getPosition().getName(),
            assignment.getScore(),
            selectionReasons
        );
    }

    private AutoScheduleShortageResponse toShortageResponse(
        RequirementTask task
    ) {
        ZoneId zoneId = ZoneId.of(
            task.workShift.getSchedulePeriod().getLocation().getTimezone()
        );
        ZonedDateTime localStart = task.workShift.getStartAt().atZone(zoneId);
        ZonedDateTime localEnd = task.workShift.getEndAt().atZone(zoneId);
        List<String> reasons = task.failureReasons.isEmpty()
            ? List.of("Không còn ứng viên hợp lệ cho vị trí")
            : task.failureReasons;
        return new AutoScheduleShortageResponse(
            task.workShift.getId(),
            shiftName(task.workShift),
            localStart.toLocalDate(),
            localStart.toLocalTime(),
            localEnd.toLocalTime(),
            localEnd.toLocalDate().isAfter(localStart.toLocalDate()),
            task.requirement.getPosition().getId(),
            task.requirement.getPosition().getName(),
            task.requirement.getMinEmployees(),
            task.assignedEmployees,
            task.requirement.getMinEmployees() - task.assignedEmployees,
            reasons
        );
    }

    private BigDecimal calculateNetHours(WorkShift workShift) {
        long netMinutes = Duration.between(
            workShift.getStartAt(),
            workShift.getEndAt()
        ).toMinutes() - workShift.getBreakMinutes();
        return BigDecimal.valueOf(netMinutes)
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator)
            .multiply(BigDecimal.valueOf(100))
            .divide(
                BigDecimal.valueOf(denominator),
                2,
                RoundingMode.HALF_UP
            );
    }

    private boolean isSchedulableStatus(WorkShift workShift) {
        return workShift.getStatus() == WorkShiftStatus.OPEN
            || workShift.getStatus() == WorkShiftStatus.FILLED;
    }

    private String shiftLabel(WorkShift workShift) {
        return "Ca " + workShift.getId() + " (" + shiftName(workShift) + ")";
    }

    private String shiftName(WorkShift workShift) {
        return workShift.getShiftTemplate() == null
            ? "Ca tùy chỉnh"
            : workShift.getShiftTemplate().getName();
    }

    private void validateSchedulable(SchedulePeriod schedulePeriod) {
        if (schedulePeriod.getStatus() != SchedulePeriodStatus.DRAFT) {
            throw new BusinessRuleException(
                "Chỉ có thể sinh lịch tự động cho kỳ đang ở trạng thái nháp"
            );
        }
        if (!schedulePeriod.getLocation().isActive()) {
            throw new BusinessRuleException(
                "Không thể sinh lịch cho chi nhánh đang ngừng hoạt động"
            );
        }
    }

    private SchedulePeriod findSchedulePeriodForUpdate(Long id) {
        return schedulePeriodRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Không tìm thấy kỳ xếp lịch có id " + id
            ));
    }

    private static final class RequirementTask {

        private final WorkShift workShift;
        private final ShiftRequirement requirement;
        private int assignedEmployees;
        private int eligibleCandidates;
        private List<String> failureReasons = List.of();

        private RequirementTask(
            WorkShift workShift,
            ShiftRequirement requirement,
            int assignedEmployees
        ) {
            this.workShift = workShift;
            this.requirement = requirement;
            this.assignedEmployees = assignedEmployees;
        }
    }

    private record CandidateEvaluation(
        User employee,
        AssignmentConstraintResult constraintResult,
        BigDecimal score,
        List<String> selectionReasons
    ) {
    }

    private record CandidateSearchResult(
        List<CandidateEvaluation> eligibleCandidates,
        List<String> failureReasons
    ) {
    }
}
