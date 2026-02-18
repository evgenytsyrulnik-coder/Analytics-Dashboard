package com.analytics.dashboard.service;

import com.analytics.dashboard.dto.AnalyticsSummaryResponse;
import com.analytics.dashboard.dto.TimeseriesResponse;
import com.analytics.dashboard.entity.AgentRun;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Stateless utility for aggregating and summarising lists of {@link AgentRun}s.
 * Extracted from AnalyticsService so that org/team/user services can share the
 * computation logic without duplicating it.
 */
public final class RunAggregator {

    private static final int COST_SCALE = 6;

    private RunAggregator() {}

    // --- Summary / Timeseries builders ---

    public static AnalyticsSummaryResponse buildSummary(UUID orgId, String from, String to, List<AgentRun> runs) {
        long total = runs.size();
        long succeeded = countByStatus(runs, "SUCCEEDED");
        long failed = countByStatus(runs, "FAILED");
        long cancelled = countByStatus(runs, "CANCELLED");
        long running = countByStatus(runs, "RUNNING");
        double successRate = total > 0 ? (double) succeeded / total : 0;

        long totalTokens = runs.stream().mapToLong(AgentRun::getTotalTokens).sum();
        long inputTokens = runs.stream().mapToLong(AgentRun::getInputTokens).sum();
        long outputTokens = runs.stream().mapToLong(AgentRun::getOutputTokens).sum();
        BigDecimal totalCost = sumCost(runs);

        List<Long> durations = runs.stream()
                .filter(r -> r.getDurationMs() != null)
                .map(AgentRun::getDurationMs)
                .sorted()
                .toList();

        long avg = durations.isEmpty() ? 0 : durations.stream().mapToLong(Long::longValue).sum() / durations.size();

        return new AnalyticsSummaryResponse(
                orgId,
                new AnalyticsSummaryResponse.PeriodRange(from, to),
                total, succeeded, failed, cancelled, running,
                Math.round(successRate * 10000.0) / 10000.0,
                totalTokens, inputTokens, outputTokens,
                formatCost(totalCost),
                avg, percentile(durations, 50), percentile(durations, 95), percentile(durations, 99)
        );
    }

    public static TimeseriesResponse buildTimeseries(UUID orgId, String granularity, List<AgentRun> runs) {
        List<TimeseriesResponse.DataPoint> points = runs.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate().toString()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    RunAggregates agg = RunAggregates.of(e.getValue());
                    return new TimeseriesResponse.DataPoint(
                            e.getKey() + "T00:00:00Z",
                            agg.totalRuns(), agg.succeeded(), agg.failed(),
                            agg.totalTokens(), agg.formattedCost(), agg.avgDurationMs()
                    );
                })
                .toList();

        return new TimeseriesResponse(orgId, granularity, points);
    }

    // --- Scalar helpers ---

    public static int computeUserRank(UUID userId, List<AgentRun> orgRuns) {
        List<Map.Entry<UUID, Long>> sorted = orgRuns.stream()
                .collect(Collectors.groupingBy(AgentRun::getUserId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .toList();
        int rank = 1;
        for (var entry : sorted) {
            if (entry.getKey().equals(userId)) break;
            rank++;
        }
        return rank;
    }

    public static int countDistinctUsers(List<AgentRun> runs) {
        return (int) runs.stream().map(AgentRun::getUserId).distinct().count();
    }

    public static long countByStatus(List<AgentRun> runs, String status) {
        return runs.stream().filter(r -> status.equals(r.getStatus())).count();
    }

    public static BigDecimal sumCost(List<AgentRun> runs) {
        return runs.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static String formatCost(BigDecimal cost) {
        return cost.setScale(COST_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    public static <K> Map<K, List<AgentRun>> groupByNonNullKey(List<AgentRun> runs,
                                                                 java.util.function.Function<AgentRun, K> keyExtractor) {
        return runs.stream()
                .filter(r -> keyExtractor.apply(r) != null)
                .collect(Collectors.groupingBy(keyExtractor));
    }

    public static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    // --- Inner helper types ---

    public record DateRange(Instant from, Instant to) {
        public static DateRange of(String from, String to) {
            return new DateRange(
                    LocalDate.parse(from).atStartOfDay().toInstant(ZoneOffset.UTC),
                    LocalDate.parse(to).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
            );
        }
    }

    /**
     * Pre-computed aggregate metrics for a list of agent runs.
     * Eliminates duplicate stream operations across breakdown methods.
     */
    public record RunAggregates(
            long totalRuns,
            long succeeded,
            long failed,
            long totalTokens,
            BigDecimal totalCost,
            double successRate,
            long avgDurationMs
    ) {
        public static RunAggregates of(List<AgentRun> runs) {
            long total = runs.size();
            long succeeded = countByStatus(runs, "SUCCEEDED");
            long failed = countByStatus(runs, "FAILED");
            long totalTokens = runs.stream().mapToLong(AgentRun::getTotalTokens).sum();
            BigDecimal totalCost = sumCost(runs);
            double successRate = total > 0 ? (double) succeeded / total : 0;
            long avgDuration = computeAvgDuration(runs);
            return new RunAggregates(total, succeeded, failed, totalTokens, totalCost, successRate, avgDuration);
        }

        public String formattedCost() {
            return formatCost(totalCost);
        }

        private static long computeAvgDuration(List<AgentRun> runs) {
            long count = runs.stream().filter(r -> r.getDurationMs() != null).count();
            if (count == 0) return 0;
            return runs.stream().filter(r -> r.getDurationMs() != null)
                    .mapToLong(AgentRun::getDurationMs).sum() / count;
        }
    }
}
