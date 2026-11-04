package com.smartshift.benchmark;

import com.google.ortools.Loader;
import com.smartshift.scheduling.HybridScheduleOptimizer;
import com.smartshift.scheduling.SchedulingProblem;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static com.smartshift.scheduling.SchedulingProblem.*;

/** Test-classpath-only CLI: never registered as an application endpoint or service. */
public final class BenchmarkCli {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--warmup")) {
            Loader.loadNativeLibraries();
            System.out.println("Native OR-Tools available");
            return;
        }
        if (args.length != 5 && args.length != 6) {
            throw new IllegalArgumentException("input.json algorithm seconds seed output.json [hybridIterations]");
        }
        JsonNode input = JSON.readTree(Files.readString(Path.of(args[0])));
        SchedulingProblem problem = read(input);
        String algorithm = args[1];
        double seconds = Double.parseDouble(args[2]);
        int seed = Integer.parseInt(args[3]);
        int hybridIterations = args.length == 6 ? Integer.parseInt(args[5]) : 1000;
        if (hybridIterations < 1 || hybridIterations > 1000) {
            throw new IllegalArgumentException("hybridIterations must be in [1, 1000]");
        }
        // Exclude native load from solver timing; process startup remains measured by the Python harness.
        Loader.loadNativeLibraries();
        long started = System.nanoTime();
        Map<String, Object> output = new LinkedHashMap<>();
        try {
            if (algorithm.equals("GREEDY_SHARED_CONSTRAINTS")) {
                Set<Integer> selected = greedy(problem, input.path("metadata").path("employeeLabels"), seconds);
                output.put("selectedOptions", selected);
                output.put("status", "HEURISTIC");
                output.put("iterations", 0);
            } else if (algorithm.equals("CP_SAT") || algorithm.equals("ALNS_CP_SAT")) {
                int iterations = algorithm.equals("CP_SAT") ? 0 : hybridIterations;
                var result = new HybridScheduleOptimizer(seconds, iterations, seed).solve(problem);
                output.put("selectedOptions", result.selectedOptions());
                output.put("status", result.status());
                output.put("iterations", result.iterations());
                output.put("internalObjective", result.objective());
                output.put("operatorWeights", result.operatorWeights());
            } else throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        } catch (RuntimeException exception) {
            output.put("selectedOptions", List.of());
            output.put("status", "ERROR");
            output.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
        output.put("algorithm", algorithm);
        output.put("seed", seed);
        output.put("timeLimitSeconds", seconds);
        output.put("maxIterations", algorithm.equals("ALNS_CP_SAT") ? hybridIterations : 0);
        output.put("solverWallMillis", (System.nanoTime() - started) / 1_000_000.0);
        output.put("javaVersion", System.getProperty("java.version"));
        output.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        Path target = Path.of(args[4]);
        Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    }

    static SchedulingProblem read(JsonNode node) {
        List<Worker> workers = new ArrayList<>();
        for (JsonNode w : node.path("workers")) workers.add(new Worker(w.path("id").asLong(),
            w.path("minimumHours").asLong(), w.path("maximumHours").asLong(), w.path("dailyHours").asLong(),
            w.path("restSeconds").asLong(), w.path("consecutiveDays").asInt(), w.path("fairnessGroup").asText()));
        List<Demand> demands = new ArrayList<>();
        for (JsonNode d : node.path("demands")) demands.add(new Demand(d.path("shiftId").asLong(),
            Instant.parse(d.path("start").asText()), Instant.parse(d.path("end").asText()),
            d.path("hours").asLong(), d.path("missing").asInt(), d.path("priority").asInt()));
        List<Option> options = new ArrayList<>();
        for (JsonNode o : node.path("options")) options.add(new Option(o.path("worker").asInt(),
            o.path("demand").asInt(), o.path("preferred").asBoolean(), o.path("dailyBase").asLong(),
            o.path("weeklyBase").asLong(), o.path("costThousands").asLong()));
        if (!node.path("fixedAssignments").isEmpty()) {
            throw new IllegalArgumentException("Benchmark v1 uses empty initial schedules; fixed work is covered by E2E tests");
        }
        JsonNode budget = node.path("weeklyBudgetThousands");
        return new SchedulingProblem(workers, demands, options, List.of(), ZoneId.of(node.path("zone").asText()),
            budget.isNull() || budget.isMissingNode() ? null : budget.asLong());
    }

    /** Replays legacy ordering/scoring, but uses the same hard rules as the new solver. */
    static Set<Integer> greedy(SchedulingProblem p, JsonNode labels, double seconds) {
        Map<Integer, List<Integer>> byDemand = new HashMap<>();
        for (int i = 0; i < p.options().size(); i++) {
            byDemand.computeIfAbsent(p.options().get(i).demand(), ignored -> new ArrayList<>()).add(i);
        }
        List<Integer> tasks = new ArrayList<>();
        for (int d = 0; d < p.demands().size(); d++) tasks.add(d);
        tasks.sort(Comparator.comparingInt((Integer d) -> p.demands().get(d).priority())
            .thenComparingInt(d -> byDemand.getOrDefault(d, List.of()).size())
            .thenComparing(d -> p.demands().get(d).start()).thenComparingInt(d -> d));
        Set<Integer> selected = new LinkedHashSet<>();
        Map<Integer, List<Integer>> byWorker = new HashMap<>();
        long deadline = System.nanoTime() + (long) (seconds * 1_000_000_000);
        for (int task : tasks) {
            for (int filled = 0; filled < p.demands().get(task).missing(); filled++) {
                if (System.nanoTime() >= deadline) return selected;
                List<Integer> candidates = byDemand.getOrDefault(task, List.of()).stream()
                    .filter(i -> feasible(p, i, byWorker.getOrDefault(p.options().get(i).worker(), List.of()))).toList();
                Comparator<Integer> comparator = Comparator
                    .comparing((Integer i) -> score(p, i, byWorker), Comparator.reverseOrder())
                    .thenComparingLong(i -> weeklyLoad(p, i, byWorker))
                    .thenComparing(i -> {
                        int worker = p.options().get(i).worker();
                        return labels.isArray() && worker < labels.size() ? labels.get(worker).asText()
                            : String.format(Locale.ROOT, "worker_%09d", p.workers().get(worker).id());
                    }).thenComparingLong(i -> p.workers().get(p.options().get(i).worker()).id());
                Integer best = candidates.stream().min(comparator).orElse(null);
                if (best == null) break;
                selected.add(best);
                byWorker.computeIfAbsent(p.options().get(best).worker(), ignored -> new ArrayList<>()).add(best);
            }
        }
        return selected;
    }

    private static BigDecimal score(SchedulingProblem p, int index, Map<Integer, List<Integer>> selected) {
        Option option = p.options().get(index);
        Worker worker = p.workers().get(option.worker());
        long current = weeklyLoad(p, index, selected);
        long projected = current + p.demands().get(option.demand()).hours();
        BigDecimal minimum = BigDecimal.valueOf(Math.min(4000, Math.max(0, worker.minimumHours() - current)), 2)
            .multiply(BigDecimal.valueOf(2));
        BigDecimal fairness = worker.maximumHours() == 0 ? BigDecimal.ZERO
            : BigDecimal.valueOf(Math.max(0, worker.maximumHours() - projected))
                .divide(BigDecimal.valueOf(worker.maximumHours()), 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(20));
        return BigDecimal.valueOf(option.preferred() ? 100 : 60).add(minimum).add(fairness)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static long weeklyLoad(SchedulingProblem p, int index, Map<Integer, List<Integer>> selected) {
        Option option = p.options().get(index);
        LocalDate week = HybridScheduleOptimizer.week(p.date(p.demands().get(option.demand())));
        long result = option.weeklyBase();
        for (int i : selected.getOrDefault(option.worker(), List.of())) {
            Demand d = p.demands().get(p.options().get(i).demand());
            if (HybridScheduleOptimizer.week(p.date(d)).equals(week)) result += d.hours();
        }
        return result;
    }

    private static boolean feasible(SchedulingProblem p, int index, List<Integer> selected) {
        Option option = p.options().get(index);
        Worker worker = p.workers().get(option.worker());
        Demand target = p.demands().get(option.demand());
        LocalDate date = p.date(target), week = HybridScheduleOptimizer.week(date);
        long daily = option.dailyBase() + target.hours(), weekly = option.weeklyBase() + target.hours();
        TreeSet<LocalDate> days = new TreeSet<>();
        days.add(date);
        for (int i : selected) {
            Demand other = p.demands().get(p.options().get(i).demand());
            if (HybridScheduleOptimizer.conflict(target, other, worker.restSeconds())) return false;
            LocalDate otherDate = p.date(other);
            if (otherDate.equals(date)) daily += other.hours();
            if (HybridScheduleOptimizer.week(otherDate).equals(week)) weekly += other.hours();
            days.add(otherDate);
        }
        if (daily > worker.dailyHours() || weekly > worker.maximumHours()) return false;
        LocalDate previous = null;
        int run = 0;
        for (LocalDate day : days) {
            run = previous != null && day.equals(previous.plusDays(1)) ? run + 1 : 1;
            if (run > worker.consecutiveDays()) return false;
            previous = day;
        }
        return true;
    }
}
