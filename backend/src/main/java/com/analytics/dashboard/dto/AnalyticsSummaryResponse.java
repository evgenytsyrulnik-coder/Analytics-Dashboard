package com.analytics.dashboard.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AnalyticsSummaryResponse(
    UUID orgId,
    PeriodRange period,
    long totalRuns,
    long succeededRuns,
    long failedRuns,
    long cancelledRuns,
    long runningRuns,
    double successRate,
    long totalTokens,
    long totalInputTokens,
    long totalOutputTokens,
    String totalCost,
    long avgDurationMs,
    long p50DurationMs,
    long p95DurationMs,
    long p99DurationMs
) {
    public record PeriodRange(String from, String to) {}
}
