package com.smartshift.scheduling;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Immutable input. Hours use hundredths, matching the shared assignment validator. */
public record SchedulingProblem(
    List<Worker> workers, List<Demand> demands, List<Option> options,
    List<FixedAssignment> fixedAssignments, ZoneId zone, Long weeklyBudgetThousands
) {
    public SchedulingProblem {
        workers = List.copyOf(workers);
        demands = List.copyOf(demands);
        options = List.copyOf(options);
        fixedAssignments = List.copyOf(fixedAssignments);
    }
    public record Worker(long id, long minimumHours, long maximumHours, long dailyHours,
                         long restSeconds, int consecutiveDays, String fairnessGroup) {}
    public record Demand(long shiftId, Instant start, Instant end, long hours,
                         int missing, int priority) {}
    public record Option(int worker, int demand, boolean preferred,
                         long dailyBase, long weeklyBase, long costThousands) {}
    public record FixedAssignment(long workerId, long shiftId, Instant start, Instant end,
                                  long hours, long costThousands) {}
    public LocalDate date(Demand demand) { return demand.start().atZone(zone).toLocalDate(); }
}
