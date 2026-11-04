package com.smartshift.dto.autoschedule;

import java.util.List;

/** Objective is an internal minimized penalty; it is not a percentage or optimality gap. */
public record OptimizationSummary(
    String algorithm, String status, long objective, int iterations,
    long elapsedMillis, int seed, List<Double> operatorWeights
) {
    public OptimizationSummary { operatorWeights = List.copyOf(operatorWeights); }
}
