package com.smartshift.scheduling;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import com.smartshift.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.stream.IntStream;

import static com.smartshift.scheduling.SchedulingProblem.*;

/**
 * CP-SAT construction + adaptive large-neighborhood search with CP-SAT repair.
 * No persistence, network calls, or mutable JPA entities inside the search.
 */
@Component
public class HybridScheduleOptimizer {
    private final double seconds;
    private final int maxIterations;
    private final int seed;
    private final boolean profiling = Boolean.getBoolean("smartshift.scheduling.profile");

    public HybridScheduleOptimizer(
        @Value("${app.scheduling.hybrid.time-limit-seconds:8}") double seconds,
        @Value("${app.scheduling.hybrid.max-iterations:20}") int maxIterations,
        @Value("${app.scheduling.hybrid.seed:42}") int seed
    ) {
        if (!Double.isFinite(seconds) || seconds < 0.1 || seconds > 60
            || maxIterations < 0 || maxIterations > 1000 || seed < 0) {
            throw new IllegalArgumentException("Invalid hybrid scheduling configuration");
        }
        this.seconds = seconds;
        this.maxIterations = maxIterations;
        this.seed = seed;
    }

    public record Result(Set<Integer> selectedOptions, long objective, int iterations,
                         long elapsedMillis, String status, int seed, List<Double> operatorWeights) {
        public Result {
            selectedOptions = Set.copyOf(selectedOptions);
            operatorWeights = List.copyOf(operatorWeights);
        }
    }
    record Model(CpModel cp, BoolVar[] variables, long coverageWeight, int freeOptions) {}
    record Solution(Set<Integer> selected, long objective, String status) {}

    public Result solve(SchedulingProblem problem) {
        long started = System.nanoTime();
        if (problem.options().isEmpty()) {
            return new Result(Set.of(), 0, 0, 0, "NO_CANDIDATES", seed, List.of());
        }
        try {
            NativeLibrary.ensureLoaded();
        } catch (LinkageError | RuntimeException error) {
            throw new BusinessRuleException(
                "Không tải được OR-Tools native library; kiểm tra kiến trúc máy và môi trường triển khai"
            );
        }
        long deadline = System.nanoTime() + (long) (seconds * 1_000_000_000L);
        long buildStarted = System.nanoTime();
        Model initial = build(problem);
        if (profiling) System.out.printf(Locale.ROOT,
            "SCHEDULING_BUILD millis=%.3f options=%d%n",
            (System.nanoTime() - buildStarted) / 1_000_000.0, problem.options().size());
        // Reserve time for ALNS only after a feasible incumbent exists. Keep the same
        // search alive when construction is difficult, avoiding another presolve.
        Solution best = run(initial, remaining(deadline), seed, maxIterations > 0);
        if (best == null) {
            throw new BusinessRuleException(
                "Bộ giải chưa tìm được lịch trong thời gian cho phép; không lưu phân công mới"
            );
        }
        if (best.status().equals("OPTIMAL")) {
            return new Result(best.selected(), best.objective(), 0,
                (System.nanoTime() - started) / 1_000_000,
                "OPTIMAL", seed, List.of(1.0, 1.0, 1.0));
        }
        Random random = new Random(seed);
        double[] weights = {1, 1, 1};
        int iterations = 0;
        for (; iterations < maxIterations && remaining(deadline) > 0.03; iterations++) {
            int operator = choose(weights, random);
            Set<Integer> relaxed = neighborhood(problem, best.selected(), operator, random);
            long repairStarted = System.nanoTime();
            Model repair = build(problem, relaxed, best.selected());
            for (int i : relaxed) {
                repair.cp().addHint(repair.variables()[i], best.selected().contains(i) ? 1 : 0);
            }
            if (profiling) System.out.printf(Locale.ROOT,
                "SCHEDULING_REPAIR iteration=%d operator=%d relaxedOptions=%d totalOptions=%d buildMillis=%.3f%n",
                iterations, operator, relaxed.size(), problem.options().size(),
                (System.nanoTime() - repairStarted) / 1_000_000.0);
            Solution candidate = run(repair,
                Math.min(0.6, remaining(deadline)), seed + iterations + 1, false);
            double reward = 0.2;
            if (candidate != null && candidate.objective() < best.objective()) {
                best = candidate;
                reward = 5;
            } else if (candidate != null && candidate.objective() == best.objective()) {
                // Keep an equally good alternative to diversify without degrading the incumbent.
                best = candidate;
                reward = 1;
            }
            if (profiling) System.out.printf(Locale.ROOT,
                "SCHEDULING_REPAIR_RESULT iteration=%d found=%s reward=%.1f bestObjective=%d%n",
                iterations, candidate != null, reward, best.objective());
            weights[operator] = 0.8 * weights[operator] + 0.2 * reward;
        }
        // OPTIMAL on a restricted neighborhood is not a global optimality certificate.
        return new Result(best.selected(), best.objective(), iterations,
            (System.nanoTime() - started) / 1_000_000,
            "FEASIBLE", seed,
            Arrays.stream(weights).boxed().toList());
    }

    private static final class NativeLibrary {
        static { Loader.loadNativeLibraries(); }
        static void ensureLoaded() {}
    }

    Solution run(Model model, double time, int randomSeed, boolean reserveForRepair) {
        if (time <= 0) return null;
        CpSolver solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(time).setNumSearchWorkers(1)
            .setRandomSeed(randomSeed).setLogSearchProgress(profiling);
        // Keep LP/default search for small problems and bounded repairs. Large roots
        // prioritize an incumbent instead of spending their whole budget in LP.
        if (model.freeOptions() >= 5000) solver.getParameters().setLinearizationLevel(0)
            .setSearchBranching(SatParameters.SearchBranching.PARTIAL_FIXED_SEARCH).setMaxPresolveIterations(1);
        CpSolverStatus status = reserveForRepair
            ? construct(solver, model.cp(), time) : solver.solve(model.cp());
        if (status == CpSolverStatus.MODEL_INVALID) {
            throw new BusinessRuleException("Mô hình xếp lịch không hợp lệ: " + model.cp().validate());
        }
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) return null;
        Set<Integer> selected = new LinkedHashSet<>();
        for (int i = 0; i < model.variables().length; i++) {
            if (model.variables()[i] != null && solver.booleanValue(model.variables()[i])) selected.add(i);
        }
        return new Solution(Set.copyOf(selected), Math.round(solver.objectiveValue()), status.name());
    }

    private CpSolverStatus construct(CpSolver solver, CpModel cp, double time) {
        long sliceNanos = (long) (time * 0.45 * 1_000_000_000L);
        ConstructionBudget budget = new ConstructionBudget(
            System.nanoTime() + sliceNanos, System::nanoTime, solver::stopSearch);
        var timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "schedule-construction-budget");
            thread.setDaemon(true);
            return thread;
        });
        var cutoff = timer.schedule(budget::onSliceExpired, sliceNanos, TimeUnit.NANOSECONDS);
        try {
            return solver.solve(cp, new CpSolverSolutionCallback() {
                @Override
                public void onSolutionCallback() { budget.onSolution(); }
            });
        } finally {
            cutoff.cancel(false);
            timer.shutdown();
        }
    }

    /** A slice ending without an incumbent must not abort construction. */
    static final class ConstructionBudget {
        private final long cutoff;
        private final LongSupplier clock;
        private final Runnable stop;
        private final AtomicBoolean hasSolution = new AtomicBoolean();
        private final AtomicBoolean stopped = new AtomicBoolean();

        ConstructionBudget(long cutoff, LongSupplier clock, Runnable stop) {
            this.cutoff = cutoff;
            this.clock = clock;
            this.stop = stop;
        }

        void onSolution() {
            hasSolution.set(true);
            if (clock.getAsLong() - cutoff >= 0) onSliceExpired();
        }

        void onSliceExpired() {
            if (hasSolution.get() && stopped.compareAndSet(false, true)) stop.run();
        }
    }

    private Model build(SchedulingProblem p) {
        return build(p, null, Set.of());
    }

    /** Same full objective and hard rules; only neighborhood options remain variable. */
    Model build(SchedulingProblem p, Set<Integer> relaxed, Set<Integer> incumbent) {
        CpModel cp = new CpModel();
        BoolVar[] x = new BoolVar[p.options().size()];
        BoolVar locked = null;
        for (int i = 0; i < x.length; i++) {
            if (relaxed == null || relaxed.contains(i)) x[i] = cp.newBoolVar("assignment_" + i);
            else if (incumbent.contains(i)) {
                if (locked == null) {
                    locked = cp.newBoolVar("locked_assignment");
                    cp.addEquality(locked, 1);
                }
                x[i] = locked;
            }
        }
        Map<Integer, List<Integer>> byDemand = new HashMap<>();
        Map<Integer, List<Integer>> byWorker = new HashMap<>();
        for (int i = 0; i < x.length; i++) {
            Option option = p.options().get(i);
            byDemand.computeIfAbsent(option.demand(), k -> new ArrayList<>()).add(i);
            byWorker.computeIfAbsent(option.worker(), k -> new ArrayList<>()).add(i);
        }
        LocalDate[] dates = p.demands().stream().map(p::date).toArray(LocalDate[]::new);
        LocalDate[] weekDates = Arrays.stream(dates).map(HybridScheduleOptimizer::week).toArray(LocalDate[]::new);
        LocalDate firstDate = Arrays.stream(dates).min(Comparator.naturalOrder()).orElse(LocalDate.EPOCH);
        // Prefer covering demands in the SAT decision tree. Propagation/backtracking
        // still enforce every hard constraint and optimize the full objective.
        Integer[] order = IntStream.range(0, x.length).filter(i -> relaxed == null || relaxed.contains(i))
            .boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingInt((Integer i) -> p.demands().get(p.options().get(i).demand()).priority())
            .thenComparingInt(i -> byDemand.get(p.options().get(i).demand()).size())
            .thenComparing(i -> p.demands().get(p.options().get(i).demand()).start())
            .thenComparingInt(i -> p.options().get(i).demand())
            .thenComparing(i -> !p.options().get(i).preferred())
            .thenComparingInt(i -> Math.floorMod(p.options().get(i).worker() -
                (int) (java.time.temporal.ChronoUnit.DAYS.between(firstDate, dates[p.options().get(i).demand()]) * 17),
                p.workers().size()))
            .thenComparingInt(i -> p.options().get(i).worker()));
        if (order.length >= 5000) cp.addDecisionStrategy(Arrays.stream(order).map(i -> x[i]).toArray(IntVar[]::new),
            DecisionStrategyProto.VariableSelectionStrategy.CHOOSE_FIRST,
            DecisionStrategyProto.DomainReductionStrategy.SELECT_MAX_VALUE);
        Map<String, Long> groupCapacity = new HashMap<>();
        Map<Long, String> workerGroups = new HashMap<>();
        for (Worker worker : p.workers()) {
            groupCapacity.merge(worker.fairnessGroup(), worker.maximumHours(), Long::sum);
            workerGroups.put(worker.id(), worker.fairnessGroup());
        }
        Map<GroupWeek, Set<Integer>> groupDemands = new HashMap<>();
        for (Option option : p.options()) {
            GroupWeek key = new GroupWeek(p.workers().get(option.worker()).fairnessGroup(), weekDates[option.demand()]);
            groupDemands.computeIfAbsent(key, ignored -> new HashSet<>()).add(option.demand());
        }
        Map<GroupWeek, Long> groupRequired = new HashMap<>();
        groupDemands.forEach((key, demands) -> {
            long required = 0;
            for (int d : demands) required += p.demands().get(d).hours() * p.demands().get(d).missing();
            groupRequired.put(key, required);
        });
        for (FixedAssignment fixed : p.fixedAssignments()) {
            String group = workerGroups.get(fixed.workerId());
            if (group != null) groupRequired.merge(
                new GroupWeek(group, week(fixed.start().atZone(p.zone()).toLocalDate())), fixed.hours(), Long::sum);
        }
        LinearExprBuilder soft = LinearExpr.newBuilder();
        long[] bound = {0};
        for (int i = 0; i < x.length; i++) {
            Option option = p.options().get(i);
            long penalty = Math.addExact(option.preferred() ? 0 : 400, option.costThousands());
            if (x[i] != null) soft.addTerm(x[i], penalty);
        }
        Map<LocalDate, Long> maxWeeklyCost = new HashMap<>();
        for (var entry : byDemand.entrySet()) {
            int d = entry.getKey(), slots = p.demands().get(d).missing();
            List<Long> penalties = new ArrayList<>(), costs = new ArrayList<>();
            for (int i : entry.getValue()) {
                Option option = p.options().get(i);
                penalties.add(Math.addExact(option.preferred() ? 0 : 400, option.costThousands()));
                costs.add(option.costThousands());
            }
            // At most `slots` options can be selected for this demand. Counting every
            // candidate inflated both objective coefficients and budget domains.
            penalties.sort(Comparator.reverseOrder());
            costs.sort(Comparator.reverseOrder());
            long maximumCost = 0;
            for (int i = 0; i < Math.min(slots, penalties.size()); i++) {
                bound[0] = Math.addExact(bound[0], penalties.get(i));
                maximumCost = Math.addExact(maximumCost, costs.get(i));
            }
            maxWeeklyCost.merge(weekDates[d], maximumCost, Math::addExact);
        }

        Map<LocalDate, List<Integer>> weeklyOptions = new HashMap<>();
        Map<LocalDate, Long> fixedCosts = new HashMap<>();
        for (FixedAssignment fixed : p.fixedAssignments()) {
            fixedCosts.merge(week(fixed.start().atZone(p.zone()).toLocalDate()),
                fixed.costThousands(), Math::addExact);
        }
        for (int w = 0; w < p.workers().size(); w++) {
            Worker worker = p.workers().get(w);
            List<Integer> options = byWorker.getOrDefault(w, List.of());
            Map<LocalDate, List<Integer>> days = new HashMap<>();
            Map<LocalDate, List<Integer>> weeks = new HashMap<>();
            for (int i : options) {
                LocalDate date = dates[p.options().get(i).demand()];
                days.computeIfAbsent(date, k -> new ArrayList<>()).add(i);
                weeks.computeIfAbsent(week(date), k -> new ArrayList<>()).add(i);
                weeklyOptions.computeIfAbsent(week(date), k -> new ArrayList<>()).add(i);
            }
            for (List<Integer> day : days.values()) {
                long base = day.stream().mapToLong(i -> p.options().get(i).dailyBase()).max().orElse(0);
                cp.addLessOrEqual(hours(p, x, day), Math.max(0, worker.dailyHours() - base));
            }
            for (Map.Entry<LocalDate, List<Integer>> entry : weeks.entrySet()) {
                List<Integer> indices = entry.getValue();
                long base = indices.stream().mapToLong(i -> p.options().get(i).weeklyBase()).max().orElse(0);
                long available = Math.max(0, worker.maximumHours() - base);
                LinearExprBuilder load = hours(p, x, indices);
                cp.addLessOrEqual(load, available);
                long minTarget = Math.min(worker.minimumHours(), worker.maximumHours());
                long standard = worker.minimumHours() > 0 ? minTarget : Math.min(4000, worker.maximumHours());
                long overtimeMax = Math.max(0, Math.max(base, worker.maximumHours()) - standard);
                long capacity = groupCapacity.getOrDefault(worker.fairnessGroup(), 0L);
                long required = groupRequired.getOrDefault(new GroupWeek(worker.fairnessGroup(), entry.getKey()), 0L);
                long target = capacity == 0 ? 0 : Math.min(worker.maximumHours(),
                    Math.round((double) required * worker.maximumHours() / capacity));
                long maxDeviation = Math.max(Math.abs(base - target),
                    Math.abs(Math.max(base, worker.maximumHours()) - target));
                if (relaxed != null && indices.stream().noneMatch(relaxed::contains)) {
                    long constant = base;
                    for (int i : indices) if (incumbent.contains(i)) constant += p.demands().get(p.options().get(i).demand()).hours();
                    soft.add(2 * Math.max(0, minTarget - constant) + 2 * Math.max(0, constant - standard)
                        + Math.abs(constant - target));
                    // Bounds remain those of the full problem, so objective scales are identical.
                    addPenalty(soft, null, 2, Math.max(0, minTarget - base), bound);
                    addPenalty(soft, null, 2, overtimeMax, bound);
                    addPenalty(soft, null, 1, maxDeviation, bound);
                    continue;
                }
                IntVar deficit = cp.newIntVar(0, Math.max(0, minTarget - base), "deficit_" + w + "_" + entry.getKey());
                cp.addMaxEquality(deficit, new LinearArgument[]{LinearExpr.constant(0),
                    LinearExpr.affine(load, -1, minTarget - base)});
                addPenalty(soft, deficit, 2, Math.max(0, minTarget - base), bound);
                IntVar overtime = cp.newIntVar(0, overtimeMax, "overtime_" + w + "_" + entry.getKey());
                cp.addMaxEquality(overtime, new LinearArgument[]{LinearExpr.constant(0),
                    LinearExpr.affine(load, 1, base - standard)});
                addPenalty(soft, overtime, 2, overtimeMax, bound);

                // Capacity-proportional workload target, within position and contract type.
                IntVar deviation = cp.newIntVar(0, maxDeviation, "fairness_" + w + "_" + entry.getKey());
                cp.addAbsEquality(deviation, LinearExpr.newBuilder().add(load).add(base - target));
                addPenalty(soft, deviation, 1, maxDeviation, bound);
            }
            // Starts/ends are fixed: maximal interval cliques encode exactly the same
            // overlap/rest exclusions without optional scheduling intervals.
            List<Integer> liveOptions = options.stream().filter(i -> x[i] != null).toList();
            for (List<Integer> clique : conflictCliques(p, worker, liveOptions)) {
                cp.addAtMostOne(clique.stream().map(i -> x[i]).toArray(Literal[]::new));
            }
            addConsecutiveDays(cp, p, worker, x, days, relaxed, incumbent);
        }
        if (p.weeklyBudgetThousands() != null) {
            for (Map.Entry<LocalDate, List<Integer>> entry : weeklyOptions.entrySet()) {
                long base = fixedCosts.getOrDefault(entry.getKey(), 0L);
                LinearExprBuilder cost = LinearExpr.newBuilder().add(base);
                long upper = Math.addExact(base, maxWeeklyCost.getOrDefault(entry.getKey(), 0L));
                for (int i : entry.getValue()) {
                    long value = p.options().get(i).costThousands();
                    if (x[i] != null) cost.addTerm(x[i], value);
                }
                long maxExcess = Math.max(0, upper - p.weeklyBudgetThousands());
                IntVar excess = cp.newIntVar(0, maxExcess, "budget_" + entry.getKey());
                cp.addMaxEquality(excess, new LinearArgument[]{LinearExpr.constant(0),
                    cost.add(-p.weeklyBudgetThousands())});
                addPenalty(soft, excess, 10, maxExcess, bound);
            }
        }
        // One uncovered slot always costs more than every soft penalty combined.
        long priorityWeight = Math.addExact(bound[0], 1);
        int maxPriority = p.demands().stream().mapToInt(Demand::priority).max().orElse(1);
        long priorityBound = 0;
        for (Demand demand : p.demands()) {
            priorityBound = Math.addExact(priorityBound,
                Math.multiplyExact((long) maxPriority - demand.priority() + 1, demand.missing()));
        }
        long coverageWeight = Math.addExact(Math.multiplyExact(priorityBound, priorityWeight),
            Math.addExact(bound[0], 1));
        LinearExprBuilder objective = LinearExpr.newBuilder().add(soft);
        long objectiveUpper = bound[0];
        for (int d = 0; d < p.demands().size(); d++) {
            Demand demand = p.demands().get(d);
            List<Integer> indices = byDemand.getOrDefault(d, List.of());
            LinearExprBuilder assigned = LinearExpr.newBuilder();
            for (int i : indices) if (x[i] != null) assigned.add(x[i]);
            IntVar missing = cp.newIntVar(0, demand.missing(), "missing_" + d);
            cp.addEquality(LinearExpr.newBuilder().add(assigned).add(missing), demand.missing());
            long coefficient = Math.addExact(coverageWeight,
                Math.multiplyExact((long) maxPriority - demand.priority() + 1, priorityWeight));
            objective.addTerm(missing, coefficient);
            objectiveUpper = Math.addExact(objectiveUpper, Math.multiplyExact(coefficient, demand.missing()));
        }
        if (objectiveUpper > (1L << 52)) {
            throw new BusinessRuleException("Quy mô/chi phí quá lớn cho thang điểm hiện tại; hãy chia kỳ lịch hoặc chi nhánh");
        }
        cp.minimize(objective);
        String validation = cp.validate();
        if (!validation.isEmpty()) throw new BusinessRuleException("Mô hình xếp lịch không hợp lệ: " + validation);
        return new Model(cp, x, coverageWeight, order.length);
    }

    private void addConsecutiveDays(CpModel cp, SchedulingProblem p, Worker worker,
                                    BoolVar[] x, Map<LocalDate, List<Integer>> days,
                                    Set<Integer> relaxed, Set<Integer> incumbent) {
        Set<LocalDate> fixedDays = new HashSet<>();
        for (FixedAssignment fixed : p.fixedAssignments()) {
            if (fixed.workerId() == worker.id()) fixedDays.add(fixed.start().atZone(p.zone()).toLocalDate());
        }
        Set<LocalDate> lockedDays = new HashSet<>();
        Map<LocalDate, BoolVar> worked = new HashMap<>();
        for (Map.Entry<LocalDate, List<Integer>> entry : days.entrySet()) {
            if (fixedDays.contains(entry.getKey())) continue;
            if (relaxed != null && entry.getValue().stream().anyMatch(i -> !relaxed.contains(i) && incumbent.contains(i))) {
                lockedDays.add(entry.getKey());
                continue;
            }
            IntVar[] assignments = entry.getValue().stream().map(i -> x[i]).filter(Objects::nonNull).toArray(IntVar[]::new);
            if (assignments.length == 0) continue;
            BoolVar day = cp.newBoolVar("worked_" + worker.id() + "_" + entry.getKey());
            cp.addMaxEquality(day, assignments);
            worked.put(entry.getKey(), day);
        }
        Set<LocalDate> starts = new HashSet<>();
        Set<LocalDate> relevant = new HashSet<>(worked.keySet());
        relevant.addAll(lockedDays);
        for (LocalDate date : relevant) {
            for (int back = 0; back <= worker.consecutiveDays(); back++) starts.add(date.minusDays(back));
        }
        for (LocalDate start : starts) {
            LinearExprBuilder sum = LinearExpr.newBuilder();
            int fixedCount = 0;
            for (int offset = 0; offset <= worker.consecutiveDays(); offset++) {
                LocalDate date = start.plusDays(offset);
                if (fixedDays.contains(date)) fixedCount++;
                else if (lockedDays.contains(date)) sum.add(1);
                else if (worked.containsKey(date)) sum.add(worked.get(date));
            }
            cp.addLessOrEqual(sum, Math.max(0, worker.consecutiveDays() - fixedCount));
        }
    }

    private record GroupWeek(String group, LocalDate week) {}
    private record Event(Instant time, boolean start, int option) {}

    static List<List<Integer>> conflictCliques(SchedulingProblem p, Worker worker, List<Integer> options) {
        List<Event> events = new ArrayList<>();
        for (int i : options) {
            Demand demand = p.demands().get(p.options().get(i).demand());
            events.add(new Event(demand.start(), true, i));
            events.add(new Event(demand.end().plusSeconds(worker.restSeconds()), false, i));
        }
        // Half-open intervals: an end at t leaves before a start at t enters.
        events.sort(Comparator.comparing(Event::time).thenComparing(Event::start).thenComparingInt(Event::option));
        Set<Integer> active = new LinkedHashSet<>();
        List<List<Integer>> cliques = new ArrayList<>();
        boolean added = false;
        for (Event event : events) {
            if (event.start()) {
                active.add(event.option());
                added = true;
            } else {
                if (added && active.size() > 1) cliques.add(List.copyOf(active));
                added = false;
                active.remove(event.option());
            }
        }
        return cliques;
    }

    private static LinearExprBuilder hours(SchedulingProblem p, BoolVar[] x, List<Integer> options) {
        LinearExprBuilder sum = LinearExpr.newBuilder();
        for (int i : options) if (x[i] != null) sum.addTerm(x[i], p.demands().get(p.options().get(i).demand()).hours());
        return sum;
    }
    private static void addPenalty(LinearExprBuilder expression, IntVar variable,
                                   long weight, long upper, long[] bound) {
        if (variable != null) expression.addTerm(variable, weight);
        bound[0] = Math.addExact(bound[0], Math.multiplyExact(weight, upper));
    }
    public static LocalDate week(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    public static boolean conflict(Demand a, Demand b, long restSeconds) {
        if (a.shiftId() == b.shiftId()) return true;
        if (a.start().isBefore(b.end()) && a.end().isAfter(b.start())) return true;
        long gap = !a.end().isAfter(b.start())
            ? Duration.between(a.end(), b.start()).getSeconds()
            : Duration.between(b.end(), a.start()).getSeconds();
        return gap < restSeconds;
    }
    private static double remaining(long deadline) {
        return (deadline - System.nanoTime()) / 1_000_000_000.0;
    }
    private static int choose(double[] weights, Random random) {
        double value = random.nextDouble() * Arrays.stream(weights).sum();
        for (int i = 0; i < weights.length; i++) {
            value -= weights[i];
            if (value <= 0) return i;
        }
        return weights.length - 1;
    }
    Set<Integer> neighborhood(SchedulingProblem p, Set<Integer> selected, int operator, Random random) {
        int limit = Math.min(800, Math.max(1, (int) Math.ceil(p.options().size() * 0.2)));
        List<Integer> candidates = new ArrayList<>();
        int anchor = random.nextInt(p.options().size());
        if (operator == 1) {
            int[] filled = new int[p.demands().size()];
            selected.forEach(i -> filled[p.options().get(i).demand()]++);
            List<Integer> shortages = IntStream.range(0, p.options().size())
                .filter(i -> filled[p.options().get(i).demand()] < p.demands().get(p.options().get(i).demand()).missing())
                .boxed().toList();
            if (!shortages.isEmpty()) anchor = shortages.get(random.nextInt(shortages.size()));
        }
        Option pivot = p.options().get(anchor);
        Demand target = p.demands().get(pivot.demand());
        LocalDate center = p.date(target);
        Set<Integer> workers = new HashSet<>();
        if (operator != 0) {
            List<Integer> pool = new ArrayList<>();
            for (Option option : p.options()) if (option.demand() == pivot.demand()) pool.add(option.worker());
            Collections.shuffle(pool, random);
            Map<Integer, Long> loads = new HashMap<>();
            LocalDate anchorWeek = week(center);
            for (int i : selected) {
                Option option = p.options().get(i);
                Demand demand = p.demands().get(option.demand());
                if (week(p.date(demand)).equals(anchorWeek)) loads.merge(option.worker(), demand.hours(), Long::sum);
            }
            for (Option option : p.options()) if (option.demand() == pivot.demand()) {
                loads.merge(option.worker(), option.weeklyBase(), Long::sum);
            }
            pool.sort(Comparator.comparingLong(w -> loads.getOrDefault(w, 0L) - p.workers().get(w).minimumHours()));
            workers.add(pivot.worker());
            int low = 0, high = pool.size() - 1;
            while (low <= high && workers.size() < 12) {
                workers.add(pool.get(low++));
                if (low <= high && workers.size() < 12) workers.add(pool.get(high--));
            }
        }
        for (int i = 0; i < p.options().size(); i++) {
            Option option = p.options().get(i);
            long offset = java.time.temporal.ChronoUnit.DAYS.between(center, p.date(p.demands().get(option.demand())));
            if (Math.abs(offset) <= 3 && (operator == 0 || workers.contains(option.worker()))) candidates.add(i);
        }
        Collections.shuffle(candidates, random);
        // Keep the shortage anchor, then its competing assignments before other alternatives.
        if (operator == 1) candidates.sort(Comparator.comparingInt((Integer i) ->
            p.options().get(i).demand() == pivot.demand() ? 0 : selected.contains(i) ? 1 : 2));
        Set<Integer> relaxed = new LinkedHashSet<>();
        relaxed.add(anchor);
        for (int i : candidates) {
            if (relaxed.size() >= limit) break;
            relaxed.add(i);
        }
        return Set.copyOf(relaxed);
    }
}
