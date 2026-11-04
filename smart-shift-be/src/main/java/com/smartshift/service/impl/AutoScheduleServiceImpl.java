package com.smartshift.service.impl;

import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.dto.autoschedule.OptimizationSummary;
import com.smartshift.scheduling.HybridPlanningService;
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
import com.smartshift.enums.SchedulePeriodStatus;
import com.smartshift.enums.ScheduleAuditAction;
import com.smartshift.enums.ScheduleAuditTargetType;
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
import com.smartshift.service.ScheduleAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AutoScheduleServiceImpl implements AutoScheduleService {

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

    private final SchedulePeriodRepository schedulePeriodRepository;
    private final WorkShiftRepository workShiftRepository;
    private final ShiftRequirementRepository shiftRequirementRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final UserRepository userRepository;
    private final ShiftAssignmentMapper shiftAssignmentMapper;
    private final AssignmentConstraintService assignmentConstraintService;
    private final ScheduleAuditService scheduleAuditService;
    private final HybridPlanningService hybridPlanningService;

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
        List<String> warnings = new ArrayList<>();
        List<RequirementTask> tasks = buildTasks(
            activeShifts,
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

        int assignedBefore = tasks.stream().mapToInt(task -> task.assignedEmployees).sum();
        List<AutoScheduleAssignmentResponse> generatedAssignments = new ArrayList<>();
        int preferredAssignments = 0;
        Map<Long, List<String>> rejectionReasons = new HashMap<>();
        HybridPlanningService.Plan plan = hybridPlanningService.plan(
            schedulePeriod, employees,
            tasks.stream().map(task -> new HybridPlanningService.DemandInput(
                task.workShift, task.requirement, task.assignedEmployees)).toList(),
            generatedAt, rejectionReasons
        );
        for (HybridPlanningService.Selection selection : plan.selections()) {
            if (!selection.shift().getStartAt().isAfter(Instant.now())) {
                throw new BusinessRuleException("Ca đã bắt đầu trong lúc tối ưu; vui lòng sinh lịch lại");
            }
            // Defense in depth: validate against persisted work and earlier selected assignments.
            AssignmentConstraintResult evaluation = assignmentConstraintService.evaluate(
                selection.employee(), selection.shift(), selection.employee().getPosition());
            if (!evaluation.eligible()) {
                throw new BusinessRuleException("Kết quả tối ưu không còn hợp lệ: "
                    + String.join("; ", evaluation.violations()));
            }
            ShiftAssignment saved = shiftAssignmentRepository.saveAndFlush(
                shiftAssignmentMapper.toAutoEntity(selection.shift(), selection.employee(),
                    generatedBy, null, String.join("; ", selection.reasons())));
            for (RequirementTask task : tasks) {
                if (task.workShift.getId().equals(selection.shift().getId())
                    && task.requirement.getPosition().getId().equals(selection.employee().getPosition().getId())) {
                    task.assignedEmployees++;
                }
            }
            generatedAssignments.add(toAssignmentResponse(saved, selection.reasons()));
            if (selection.preferred()) preferredAssignments++;
        }
        for (RequirementTask task : tasks) {
            if (task.assignedEmployees < task.requirement.getMinEmployees()) {
                List<String> reasons = rejectionReasons.getOrDefault(task.requirement.getId(), List.of());
                task.failureReasons = reasons.isEmpty()
                    ? List.of("Chưa thể lấp đủ trong giới hạn giờ, thời gian nghỉ, ngày liên tiếp và thời gian tìm kiếm")
                    : reasons;
            }
        }
        if (!plan.result().status().equals("OPTIMAL") && !plan.result().status().equals("NO_CANDIDATES")) {
            warnings.add("Đã tìm được lịch khả thi; chưa chứng minh tối ưu toàn cục trong thời gian cho phép.");
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

        AutoScheduleResponse response = new AutoScheduleResponse(
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
            shortages,
            new OptimizationSummary("ALNS_CP_SAT", plan.result().status(),
                plan.result().objective(), plan.result().iterations(), plan.result().elapsedMillis(),
                plan.result().seed(), plan.result().operatorWeights())
        );
        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("generatedAt", generatedAt);
        afterData.put("assignmentsCreated", generatedAssignments.size());
        afterData.put("assignedEmployeesBefore", assignedBefore);
        afterData.put("assignedEmployeesAfter", assignedAfter);
        afterData.put("coveragePercentage", coveragePercentage);
        afterData.put("qualityScore", qualityScore);
        afterData.put("optimization", response.optimization());
        afterData.put(
            "assignmentIds",
            generatedAssignments.stream()
                .map(AutoScheduleAssignmentResponse::assignmentId)
                .toList()
        );
        scheduleAuditService.record(
            generatedByUsername,
            schedulePeriod,
            null,
            ScheduleAuditAction.AUTO_SCHEDULED,
            ScheduleAuditTargetType.SCHEDULE_PERIOD,
            schedulePeriod.getId(),
            null,
            null,
            afterData
        );
        return response;
    }

    private List<RequirementTask> buildTasks(
        List<WorkShift> workShifts,
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

}
