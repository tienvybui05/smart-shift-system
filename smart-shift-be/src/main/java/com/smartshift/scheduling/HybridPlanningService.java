package com.smartshift.scheduling;

import com.smartshift.entity.*;
import com.smartshift.dto.assignment.AssignmentConstraintResult;
import com.smartshift.enums.*;
import com.smartshift.exception.BusinessRuleException;
import com.smartshift.repository.ShiftAssignmentRepository;
import com.smartshift.service.AssignmentConstraintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

import static com.smartshift.scheduling.SchedulingProblem.*;

/** Builds an immutable solver snapshot while the caller holds period/employee locks. */
@Service
@RequiredArgsConstructor
public class HybridPlanningService {
    private final AssignmentConstraintService constraintService;
    private final ShiftAssignmentRepository assignmentRepository;
    private final HybridScheduleOptimizer optimizer;

    public record DemandInput(WorkShift shift, ShiftRequirement requirement, int assigned) {}
    public record Selection(User employee, WorkShift shift, boolean preferred, List<String> reasons) {}
    public record Plan(List<Selection> selections, HybridScheduleOptimizer.Result result) {}

    public Plan plan(SchedulePeriod period, List<User> employees, List<DemandInput> inputs,
                     Instant generatedAt, Map<Long, List<String>> rejectionReasons) {
        ZoneId zone = ZoneId.of(period.getLocation().getTimezone());
        List<Worker> workers = new ArrayList<>();
        List<User> schedulable = employees.stream().filter(e -> e.getPosition() != null).toList();
        for (User employee : schedulable) {
            workers.add(new Worker(employee.getId(), units(employee.getMinHoursPerWeek()),
                units(employee.getMaxHoursPerWeek()), units(employee.getMaxHoursPerDay()),
                employee.getMinRestHours().multiply(BigDecimal.valueOf(3600)).longValueExact(),
                employee.getMaxConsecutiveDays(), employee.getPosition().getId() + ":" + employee.getEmploymentType()));
        }
        List<Demand> demands = new ArrayList<>();
        List<Option> options = new ArrayList<>();
        List<WorkShift> shifts = new ArrayList<>();
        int historyDays = Math.max(7, workers.stream().mapToInt(Worker::consecutiveDays).max().orElse(7) + 1);
        long restSeconds = workers.stream().mapToLong(Worker::restSeconds).max().orElse(0);
        LocalDate firstWeek = HybridScheduleOptimizer.week(period.getStartDate());
        LocalDate lastWeek = HybridScheduleOptimizer.week(period.getEndDate()).plusWeeks(1);
        Instant from = firstWeek.minusDays(historyDays).atStartOfDay(zone).toInstant().minusSeconds(restSeconds);
        Instant to = lastWeek.plusDays(historyDays).atStartOfDay(zone).toInstant().plusSeconds(restSeconds);
        List<ShiftAssignment> fixed = assignmentRepository.findActiveForLocationInRange(
            period.getLocation().getId(),
            schedulable.isEmpty() ? List.of(-1L) : schedulable.stream().map(User::getId).toList(),
            List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED), from, to);
        List<FixedAssignment> fixedAssignments = fixed.stream()
            .filter(a -> a.getWorkShift().getStatus() != WorkShiftStatus.CANCELLED)
            .map(a -> new FixedAssignment(a.getUser().getId(), a.getWorkShift().getId(),
                a.getWorkShift().getStartAt(), a.getWorkShift().getEndAt(), hours(a.getWorkShift()),
                a.getWorkShift().getSchedulePeriod().getLocation().getId().equals(period.getLocation().getId())
                    ? cost(a.getUser(), a.getWorkShift()) : 0)).toList();

        AssignmentConstraintService.BatchEvaluator evaluator = constraintService.prepare(
            schedulable, inputs.stream().map(DemandInput::shift).distinct().toList(), fixed);

        // Include inactive assignment rows too: DB uniqueness applies to every status.
        List<Long> shiftIds = inputs.stream().map(i -> i.shift().getId()).distinct().toList();
        Set<String> occupied = new HashSet<>();
        if (!shiftIds.isEmpty()) {
            assignmentRepository.findAllByWorkShiftIdsAndStatusIn(shiftIds, List.of(AssignmentStatus.values()))
                .forEach(a -> occupied.add(a.getUser().getId() + ":" + a.getWorkShift().getId()));
        }
        for (DemandInput input : inputs) {
            WorkShift shift = input.shift();
            int missing = Math.max(0, input.requirement().getMinEmployees() - input.assigned());
            if (missing == 0) continue;
            int d = demands.size();
            shifts.add(shift);
            demands.add(new Demand(shift.getId(), shift.getStartAt(), shift.getEndAt(),
                hours(shift), missing, input.requirement().getPriority()));
            LinkedHashSet<String> reasons = new LinkedHashSet<>();
            if (!shift.getStartAt().isAfter(generatedAt)) {
                reasons.add("Ca đã bắt đầu hoặc đang diễn ra");
            } else {
                for (int w = 0; w < schedulable.size(); w++) {
                    User employee = schedulable.get(w);
                    if (!employee.getPosition().getId().equals(input.requirement().getPosition().getId())) continue;
                    if (occupied.contains(employee.getId() + ":" + shift.getId())) continue;
                    // Fixed work is checked once per candidate; speculative work stays in CP-SAT.
                    AssignmentConstraintResult evaluation = evaluator.evaluate(
                        employee, shift, input.requirement().getPosition());
                    if (!evaluation.eligible()) {
                        reasons.addAll(evaluation.violations());
                        continue;
                    }
                    if (fixedConflict(employee, shift, fixedAssignments)) {
                        reasons.add("Không đủ thời gian nghỉ hoặc trùng phân công đã có");
                        continue;
                    }
                    options.add(new Option(w, d, evaluation.availabilityType() == AvailabilityType.PREFERRED,
                        Math.max(0, units(evaluation.projectedDailyHours()) - hours(shift)),
                        Math.max(0, units(evaluation.projectedWeeklyHours()) - hours(shift)), cost(employee, shift)));
                }
            }
            rejectionReasons.put(input.requirement().getId(), reasons.stream().limit(5).toList());
        }
        Long budget = period.getLocation().getWeeklyBudget() == null ? null
            : period.getLocation().getWeeklyBudget().divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP).longValueExact();
        SchedulingProblem problem = new SchedulingProblem(workers, demands, options, fixedAssignments, zone, budget);
        HybridScheduleOptimizer.Result result;
        try {
            result = optimizer.solve(problem);
        } catch (ArithmeticException exception) {
            throw new BusinessRuleException("Dữ liệu giờ làm/chi phí quá lớn cho mô hình tối ưu");
        }
        List<Selection> selections = result.selectedOptions().stream().sorted().map(index -> {
            Option option = options.get(index);
            User employee = schedulable.get(option.worker());
            WorkShift shift = shifts.get(option.demand());
            return new Selection(employee, shift, option.preferred(), List.of(
                "Phân công từ thuật toán lai ALNS–CP-SAT",
                option.preferred() ? "Đăng ký ưu tiên cho toàn bộ thời gian ca" : "Đăng ký rảnh cho toàn bộ thời gian ca",
                "Tối ưu toàn lịch theo độ phủ, định mức giờ, công bằng, nguyện vọng và chi phí"
            ));
        }).toList();
        return new Plan(selections, result);
    }

    private boolean fixedConflict(User employee, WorkShift shift, List<FixedAssignment> fixed) {
        Demand target = new Demand(shift.getId(), shift.getStartAt(), shift.getEndAt(), hours(shift), 1, 1);
        long rest = employee.getMinRestHours().multiply(BigDecimal.valueOf(3600)).longValueExact();
        return fixed.stream().filter(a -> a.workerId() == employee.getId())
            .anyMatch(a -> HybridScheduleOptimizer.conflict(target,
                new Demand(a.shiftId(), a.start(), a.end(), a.hours(), 1, 1), rest,
                ZoneId.of(shift.getSchedulePeriod().getLocation().getTimezone())));
    }
    public static long units(BigDecimal hours) {
        return hours.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
    public static long hours(WorkShift shift) {
        long minutes = Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes() - shift.getBreakMinutes();
        return BigDecimal.valueOf(minutes).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP).longValueExact();
    }
    private static long cost(User employee, WorkShift shift) {
        long minutes = Duration.between(shift.getStartAt(), shift.getEndAt()).toMinutes() - shift.getBreakMinutes();
        return employee.getBasePayAmount().multiply(employee.getSalaryCoefficient())
            .multiply(BigDecimal.valueOf(minutes))
            .divide(BigDecimal.valueOf(60000), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
