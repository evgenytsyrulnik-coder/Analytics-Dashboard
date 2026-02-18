package com.analytics.dashboard.service;

import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.AgentType;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.AgentTypeRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private static final int COST_SCALE = 6;

    private final AgentRunRepository agentRunRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AgentTypeRepository agentTypeRepository;

    public AnalyticsService(AgentRunRepository agentRunRepository,
                            TeamRepository teamRepository,
                            UserRepository userRepository,
                            AgentTypeRepository agentTypeRepository) {
        this.agentRunRepository = agentRunRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.agentTypeRepository = agentTypeRepository;
    }

    // --- Public API ---

    public AnalyticsSummaryResponse getOrgSummary(UUID orgId, String from, String to,
                                                   UUID teamId, String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), teamId, agentType, status);
        return buildSummary(orgId, from, to, runs);
    }

    public AnalyticsSummaryResponse getTeamSummary(UUID teamId, String from, String to,
                                                    String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, range.from(), range.to(), agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();
        return buildSummary(team.getOrgId(), from, to, runs);
    }

    public UserSummaryResponse getUserSummary(UUID userId, UUID orgId, String from, String to,
                                               String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, range.from(), range.to(), agentType, status);
        RunAggregates agg = RunAggregates.of(runs);

        List<AgentRun> orgRuns = agentRunRepository.findFiltered(orgId, range.from(), range.to(), null, null, null);
        int rank = computeUserRank(userId, orgRuns);
        int teamSize = countDistinctUsers(orgRuns);

        return new UserSummaryResponse(
                userId,
                new AnalyticsSummaryResponse.PeriodRange(from, to),
                agg.totalRuns(), agg.succeeded(), agg.failed(), agg.totalTokens(),
                agg.formattedCost(),
                agg.avgDurationMs(), rank, teamSize
        );
    }

    public TimeseriesResponse getOrgTimeseries(UUID orgId, String from, String to,
                                                UUID teamId, String agentType, String status, String granularity) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), teamId, agentType, status);
        return buildTimeseries(orgId, granularity != null ? granularity : "DAILY", runs);
    }

    public TimeseriesResponse getTeamTimeseries(UUID teamId, String from, String to,
                                                 String agentType, String status, String granularity) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, range.from(), range.to(), agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();
        return buildTimeseries(team.getOrgId(), granularity != null ? granularity : "DAILY", runs);
    }

    public TimeseriesResponse getUserTimeseries(UUID userId, String from, String to,
                                                 String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, range.from(), range.to(), agentType, status);
        return buildTimeseries(null, "DAILY", runs);
    }

    public ByTeamResponse getByTeam(UUID orgId, String from, String to, String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), null, agentType, status);

        Map<UUID, Team> teams = teamRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        List<ByTeamResponse.TeamBreakdown> breakdowns = groupByNonNullKey(runs, AgentRun::getTeamId)
                .entrySet().stream()
                .map(e -> {
                    Team team = teams.get(e.getKey());
                    RunAggregates agg = RunAggregates.of(e.getValue());
                    return new ByTeamResponse.TeamBreakdown(
                            e.getKey(),
                            team != null ? team.getName() : "Unknown",
                            agg.totalRuns(), agg.totalTokens(), agg.formattedCost(),
                            agg.successRate(), agg.avgDurationMs()
                    );
                })
                .sorted(Comparator.comparingLong(ByTeamResponse.TeamBreakdown::totalRuns).reversed())
                .toList();

        return new ByTeamResponse(orgId, new AnalyticsSummaryResponse.PeriodRange(from, to), breakdowns);
    }

    public ByAgentTypeResponse getByAgentType(UUID orgId, String from, String to, UUID teamId, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), teamId, null, status);

        Map<String, AgentType> types = agentTypeRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(AgentType::getSlug, t -> t));

        List<ByAgentTypeResponse.AgentTypeBreakdown> breakdowns = runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getAgentTypeSlug))
                .entrySet().stream()
                .map(e -> {
                    AgentType at = types.get(e.getKey());
                    RunAggregates agg = RunAggregates.of(e.getValue());
                    return new ByAgentTypeResponse.AgentTypeBreakdown(
                            e.getKey(),
                            at != null ? at.getDisplayName() : e.getKey(),
                            agg.totalRuns(), agg.totalTokens(), agg.formattedCost(),
                            agg.successRate(), agg.avgDurationMs()
                    );
                })
                .sorted(Comparator.comparingLong(ByAgentTypeResponse.AgentTypeBreakdown::totalRuns).reversed())
                .toList();

        return new ByAgentTypeResponse(orgId, new AnalyticsSummaryResponse.PeriodRange(from, to), breakdowns);
    }

    public TopUsersResponse getTopUsers(UUID orgId, String from, String to,
                                         UUID teamId, String sortBy, int limit) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), teamId, null, null);

        Map<UUID, User> users = userRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Team> teams = teamRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        String effectiveSortBy = sortBy != null ? sortBy : "runs";

        Comparator<TopUsersResponse.UserMetric> comp = switch (effectiveSortBy) {
            case "tokens" -> Comparator.comparingLong(TopUsersResponse.UserMetric::totalTokens).reversed();
            case "cost" -> Comparator.comparing(TopUsersResponse.UserMetric::totalCost).reversed();
            default -> Comparator.comparingLong(TopUsersResponse.UserMetric::totalRuns).reversed();
        };

        List<TopUsersResponse.UserMetric> userMetrics = runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getUserId))
                .entrySet().stream()
                .map(e -> {
                    User u = users.get(e.getKey());
                    List<AgentRun> userRuns = e.getValue();
                    UUID firstTeamId = userRuns.stream().map(AgentRun::getTeamId)
                            .filter(Objects::nonNull).findFirst().orElse(null);
                    String teamName = firstTeamId != null && teams.containsKey(firstTeamId) ?
                            teams.get(firstTeamId).getName() : "Unknown";
                    return new TopUsersResponse.UserMetric(
                            e.getKey(),
                            u != null ? u.getDisplayName() : "Unknown",
                            u != null ? u.getEmail() : "",
                            teamName,
                            userRuns.size(),
                            userRuns.stream().mapToLong(AgentRun::getTotalTokens).sum(),
                            formatCost(sumCost(userRuns))
                    );
                })
                .sorted(comp)
                .limit(limit)
                .toList();

        return new TopUsersResponse(orgId, effectiveSortBy, userMetrics);
    }

    public ByTeamResponse getTeamByUser(UUID teamId, String from, String to, String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, range.from(), range.to(), agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();

        Map<UUID, User> users = userRepository.findByOrgId(team.getOrgId()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ByTeamResponse.TeamBreakdown> breakdowns = runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getUserId))
                .entrySet().stream()
                .map(e -> {
                    User u = users.get(e.getKey());
                    RunAggregates agg = RunAggregates.of(e.getValue());
                    return new ByTeamResponse.TeamBreakdown(
                            e.getKey(),
                            u != null ? u.getDisplayName() : "Unknown",
                            agg.totalRuns(), agg.totalTokens(), agg.formattedCost(),
                            agg.successRate(), agg.avgDurationMs()
                    );
                })
                .sorted(Comparator.comparingLong(ByTeamResponse.TeamBreakdown::totalRuns).reversed())
                .toList();

        return new ByTeamResponse(team.getOrgId(), new AnalyticsSummaryResponse.PeriodRange(from, to), breakdowns);
    }

    public RunListResponse getUserRuns(UUID userId, String from, String to,
                                        String agentType, String status, int limit) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, range.from(), range.to(), agentType, status);

        Map<String, AgentType> types = new HashMap<>();
        if (!runs.isEmpty()) {
            UUID orgId = runs.get(0).getOrgId();
            agentTypeRepository.findByOrgId(orgId).forEach(at -> types.put(at.getSlug(), at));
        }

        List<AgentRun> limited = runs.stream().limit(limit).toList();
        boolean hasMore = runs.size() > limit;

        List<RunListResponse.RunSummary> summaries = limited.stream()
                .map(r -> new RunListResponse.RunSummary(
                        r.getId(),
                        r.getAgentTypeSlug(),
                        types.containsKey(r.getAgentTypeSlug()) ? types.get(r.getAgentTypeSlug()).getDisplayName() : r.getAgentTypeSlug(),
                        r.getStatus(),
                        r.getStartedAt().toString(),
                        r.getFinishedAt() != null ? r.getFinishedAt().toString() : null,
                        r.getDurationMs() != null ? r.getDurationMs() : 0,
                        r.getTotalTokens(),
                        formatCost(r.getTotalCost())
                ))
                .toList();

        return new RunListResponse(summaries, null, hasMore);
    }

    public RunDetailResponse getRunDetail(UUID runId) {
        AgentRun r = agentRunRepository.findById(runId).orElseThrow(
                () -> new NoSuchElementException("Run not found: " + runId));
        AgentType at = agentTypeRepository.findByOrgIdAndSlug(r.getOrgId(), r.getAgentTypeSlug()).orElse(null);
        return new RunDetailResponse(
                r.getId(), r.getOrgId(), r.getTeamId(), r.getUserId(),
                r.getAgentTypeSlug(),
                at != null ? at.getDisplayName() : r.getAgentTypeSlug(),
                r.getModelName(), r.getModelVersion(), r.getStatus(),
                r.getStartedAt().toString(),
                r.getFinishedAt() != null ? r.getFinishedAt().toString() : null,
                r.getDurationMs() != null ? r.getDurationMs() : 0,
                r.getInputTokens(), r.getOutputTokens(), r.getTotalTokens(),
                formatCost(r.getInputCost()),
                formatCost(r.getOutputCost()),
                formatCost(r.getTotalCost()),
                r.getErrorCategory(), r.getErrorMessage()
        );
    }

    // --- Private helpers ---

    private AnalyticsSummaryResponse buildSummary(UUID orgId, String from, String to, List<AgentRun> runs) {
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

    private TimeseriesResponse buildTimeseries(UUID orgId, String granularity, List<AgentRun> runs) {
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

    private static int computeUserRank(UUID userId, List<AgentRun> orgRuns) {
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

    private static int countDistinctUsers(List<AgentRun> runs) {
        return (int) runs.stream().map(AgentRun::getUserId).distinct().count();
    }

    private static long countByStatus(List<AgentRun> runs, String status) {
        return runs.stream().filter(r -> status.equals(r.getStatus())).count();
    }

    private static BigDecimal sumCost(List<AgentRun> runs) {
        return runs.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String formatCost(BigDecimal cost) {
        return cost.setScale(COST_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Groups runs by a non-null key extracted from each run.
     */
    private static <K> Map<K, List<AgentRun>> groupByNonNullKey(List<AgentRun> runs,
                                                                 java.util.function.Function<AgentRun, K> keyExtractor) {
        return runs.stream()
                .filter(r -> keyExtractor.apply(r) != null)
                .collect(Collectors.groupingBy(keyExtractor));
    }

    private static long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    // --- Inner helper types ---

    private record DateRange(Instant from, Instant to) {
        static DateRange of(String from, String to) {
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
    private record RunAggregates(
            long totalRuns,
            long succeeded,
            long failed,
            long totalTokens,
            BigDecimal totalCost,
            double successRate,
            long avgDurationMs
    ) {
        static RunAggregates of(List<AgentRun> runs) {
            long total = runs.size();
            long succeeded = countByStatus(runs, "SUCCEEDED");
            long failed = countByStatus(runs, "FAILED");
            long totalTokens = runs.stream().mapToLong(AgentRun::getTotalTokens).sum();
            BigDecimal totalCost = sumCost(runs);
            double successRate = total > 0 ? (double) succeeded / total : 0;
            long avgDuration = computeAvgDuration(runs);
            return new RunAggregates(total, succeeded, failed, totalTokens, totalCost, successRate, avgDuration);
        }

        String formattedCost() {
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
