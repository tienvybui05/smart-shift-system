package com.smartshift.benchmark;

import com.smartshift.scheduling.SchedulingProblem;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static com.smartshift.scheduling.SchedulingProblem.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BenchmarkCliTest {
    @Test
    void sharedGreedyDoesNotReusePreferredEmployeeOnOverlappingShift() throws Exception {
        var workers = List.of(worker(1, 5), worker(2, 5));
        var demands = List.of(demand(1, "2030-01-07T06:00:00Z"), demand(2, "2030-01-07T07:00:00Z"));
        var options = List.of(new Option(0, 0, true, 0, 0, 0), new Option(1, 0, false, 0, 0, 0),
            new Option(0, 1, true, 0, 0, 0), new Option(1, 1, false, 0, 0, 0));
        var problem = new SchedulingProblem(workers, demands, options, List.of(), ZoneId.of("UTC"), null);
        assertEquals(Set.of(0, 3), BenchmarkCli.greedy(problem, JsonMapper.builder().build().readTree("[]"), 5));
    }

    @Test
    void sharedGreedyAppliesConsecutiveDayConstraintAcrossWeekBoundary() throws Exception {
        var demands = List.of(demand(1, "2030-01-13T06:00:00Z"), demand(2, "2030-01-14T06:00:00Z"));
        var options = List.of(new Option(0, 0, true, 0, 0, 0), new Option(0, 1, true, 0, 0, 0));
        var problem = new SchedulingProblem(List.of(worker(1, 1)), demands, options, List.of(), ZoneId.of("UTC"), null);
        assertEquals(Set.of(0), BenchmarkCli.greedy(problem, JsonMapper.builder().build().readTree("[]"), 5));
    }

    private Worker worker(long id, int consecutive) {
        return new Worker(id, 3200, 4000, 800, 39600, consecutive, "group");
    }

    private Demand demand(long id, String start) {
        Instant instant = Instant.parse(start);
        return new Demand(id, instant, instant.plusSeconds(8*3600), 800, 1, 1);
    }
}
