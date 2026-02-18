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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

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

    public AnalyticsSummaryResponse getOrgSummary(UUID orgId, String from, String to,
                                                   UUID teamId, String agentType, String status) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, fromInstant, toInstant, teamId, agentType, status);
        return buildSummary(orgId, from, to, runs);
    }

    public AnalyticsSummaryResponse getTeamSummary(UUID teamId, String from, String to,
                                                    String agentType, String status) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, fromInstant, toInstant, agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();
        return buildSummary(team.getOrgId(), from, to, runs);
    }

    public UserSummaryResponse getUserSummary(UUID userId, UUID orgId, String from, String to,
                                               String agentType, String status) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, fromInstant, toInstant, agentType, status);

        long total = runs.size();
        long succeeded = runs.stream().filter(r -> "SUCCEEDED".equals(r.getStatus())).count();
        long failed = runs.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long totalTokens = runs.stream().mapToLong(AgentRun::getTotalTokens).sum();
        BigDecimal totalCost = runs.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        long avgDuration = runs.isEmpty() ? 0 :
                runs.stream().filter(r -> r.getDurationMs() != null).mapToLong(AgentRun::getDurationMs).sum() / Math.max(1, runs.stream().filter(r -> r.getDurationMs() != null).count());

        // Compute team rank
        User user = userRepository.findById(userId).orElseThrow();
        List<AgentRun> orgRuns = agentRunRepository.findFiltered(orgId, fromInstant, toInstant, null, null, null);
        Map<UUID, Long> userRunCounts = orgRuns.stream()
                .collect(Collectors.groupingBy(AgentRun::getUserId, Collectors.counting()));
        List<Map.Entry<UUID, Long>> sorted = userRunCounts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .toList();
        int rank = 1;
        for (var entry : sorted) {
            if (entry.getKey().equals(userId)) break;
            rank++;
        }

        return new UserSummaryResponse(
                userId,
                new AnalyticsSummaryResponse.PeriodRange(from, to),
                total, succeeded, failed, totalTokens,
                totalCost.setScale(6, RoundingMode.HALF_UP).toPlainString(),
                avgDuration, rank, userRunCounts.size()
        );
    }

    public TimeseriesResponse getOrgTimeseries(UUID orgId, String from, String to,
                                                UUID teamId, String agentType, String status, String granularity) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, fromInstant, toInstant, teamId, agentType, status);
        return buildTimeseries(orgId, granularity != null ? granularity : "DAILY", runs);
    }

    public TimeseriesResponse getTeamTimeseries(UUID teamId, String from, String to,
                                                 String agentType, String status, String granularity) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, fromInstant, toInstant, agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();
        return buildTimeseries(team.getOrgId(), granularity != null ? granularity : "DAILY", runs);
    }

    public TimeseriesResponse getUserTimeseries(UUID userId, String from, String to,
                                                 String agentType, String status) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, fromInstant, toInstant, agentType, status);
        return buildTimeseries(null, "DAILY", runs);
    }

    public ByTeamResponse getByTeam(UUID orgId, String from, String to, String agentType, String status) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, fromInstant, toInstant, null, agentType, status);
        Map<UUID, List<AgentRun>> byTeam = runs.stream()
                .filter(r -> r.getTeamId() != null)
                .collect(Collectors.groupingBy(AgentRun::getTeamId));

        Map<UUID, Team> teams = teamRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        List<ByTeamResponse.TeamBreakdown> breakdowns = byTeam.entrySet().stream()
                .map(e -> {
                    Team team = teams.get(e.getKey());
                    List<AgentRun> teamRuns = e.getValue();
                    long succeeded = teamRuns.stream().filter(r -> "SUCCEEDED".equals(r.getStatus())).count();
                    return new ByTeamResponse.TeamBreakdown(
                            e.getKey(),
                            team != null ? team.getName() : "Unknown",
                            teamRuns.size(),
                            teamRuns.stream().mapToLong(AgentRun::getTotalTokens).sum(),
                            teamRuns.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .setScale(6, RoundingMode.HALF_UP).toPlainString(),
                            teamRuns.isEmpty() ? 0 : (double) succeeded / teamRuns.size(),
                            teamRuns.isEmpty() ? 0 : teamRuns.stream().filter(r -> r.getDurationMs() != null)
                                    .mapToLong(AgentRun::getDurationMs).sum() /
                                    Math.max(1, teamRuns.stream().filter(r -> r.getDurationMs() != null).count())
                    );
                })
                .sorted(Comparator.comparingLong(ByTeamResponse.TeamBreakdown::totalRuns).reversed())
                .toList();

        return new ByTeamResponse(orgId, new AnalyticsSummaryResponse.PeriodRange(from, to), breakdowns);
    }

    public ByAgentTypeResponse getByAgentType(UUID orgId, String from, String to, UUID teamId, String status) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, fromInstant, toInstant, teamId, null, status);
        Map<String, List<AgentRun>> byType = runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getAgentTypeSlug));

        Map<String, AgentType> types = agentTypeRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(AgentType::getSlug, t -> t));

        List<ByAgentTypeResponse.AgentTypeBreakdown> breakdowns = byType.entrySet().stream()
                .map(e -> {
                    AgentType at = types.get(e.getKey());
                    List<AgentRun> typeRuns = e.getValue();
                    long succeeded = typeRuns.stream().filter(r -> "SUCCEEDED".equals(r.getStatus())).count();
                    return new ByAgentTypeResponse.AgentTypeBreakdown(
                            e.getKey(),
                            at != null ? at.getDisplayName() : e.getKey(),
                            typeRuns.size(),
                            typeRuns.stream().mapToLong(AgentRun::getTotalTokens).sum(),
                            typeRuns.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .setScale(6, RoundingMode.HALF_UP).toPlainString(),
                            typeRuns.isEmpty() ? 0 : (double) succeeded / typeRuns.size(),
                            typeRuns.isEmpty() ? 0 : typeRuns.stream().filter(r -> r.getDurationMs() != null)
                                    .mapToLong(AgentRun::getDurationMs).sum() /
                                    Math.max(1, typeRuns.stream().filter(r -> r.getDurationMs() != null).count())
                    );
                })
                .sorted(Comparator.comparingLong(ByAgentTypeResponse.AgentTypeBreakdown::totalRuns).reversed())
                .toList();

        return new ByAgentTypeResponse(orgId, new AnalyticsSummaryResponse.PeriodRange(from, to), breakdowns);
    }

    public TopUsersResponse getTopUsers(UUID orgId, String from, String to,
                                         UUID teamId, String sortBy, int limit) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, fromInstant, toInstant, teamId, null, null);

        Map<UUID, List<AgentRun>> byUser = runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getUserId));
        Map<UUID, User> users = userRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Team> teams = teamRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        Comparator<TopUsersResponse.UserMetric> comp = switch (sortBy != null ? sortBy : "runs") {
            case "tokens" -> Comparator.comparingLong(TopUsersResponse.UserMetric::totalTokens).reversed();
            case "cost" -> Comparator.comparing(TopUsersResponse.UserMetric::totalCost).reversed();
            default -> Comparator.comparingLong(TopUsersResponse.UserMetric::totalRuns).reversed();
        };

        List<TopUsersResponse.UserMetric> userMetrics = byUser.entrySet().stream()
                .map(e -> {
                    User u = users.get(e.getKey());
                    List<AgentRun> userRuns = e.getValue();
                    UUID firstTeamId = userRuns.stream().map(AgentRun::getTeamId).filter(Objects::nonNull).findFirst().orElse(null);
                    String teamName = firstTeamId != null && teams.containsKey(firstTeamId) ?
                            teams.get(firstTeamId).getName() : "Unknown";
                    return new TopUsersResponse.UserMetric(
                            e.getKey(),
                            u != null ? u.getDisplayName() : "Unknown",
                            u != null ? u.getEmail() : "",
                            teamName,
                            userRuns.size(),
                            userRuns.stream().mapToLong(AgentRun::getTotalTokens).sum(),
                            userRuns.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .setScale(6, RoundingMode.HALF_UP).toPlainString()
                    );
                })
                .sorted(comp)
                .limit(limit)
                .toList();

        return new TopUsersResponse(orgId, sortBy != null ? sortBy : "runs", userMetrics);
    }

    public ByTeamResponse getTeamByUser(UUID teamId, String from, String to, String agentType, String status) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, fromInstant, toInstant, agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();

        Map<UUID, List<AgentRun>> byUser = runs.stream()
                .collect(Collectors.groupingBy(AgentRun::getUserId));
        Map<UUID, User> users = userRepository.findByOrgId(team.getOrgId()).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<ByTeamResponse.TeamBreakdown> breakdowns = byUser.entrySet().stream()
                .map(e -> {
                    User u = users.get(e.getKey());
                    List<AgentRun> userRuns = e.getValue();
                    long succeeded = userRuns.stream().filter(r -> "SUCCEEDED".equals(r.getStatus())).count();
                    return new ByTeamResponse.TeamBreakdown(
                            e.getKey(),
                            u != null ? u.getDisplayName() : "Unknown",
                            userRuns.size(),
                            userRuns.stream().mapToLong(AgentRun::getTotalTokens).sum(),
                            userRuns.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add)
                                    .setScale(6, RoundingMode.HALF_UP).toPlainString(),
                            userRuns.isEmpty() ? 0 : (double) succeeded / userRuns.size(),
                            userRuns.isEmpty() ? 0 : userRuns.stream().filter(r -> r.getDurationMs() != null)
                                    .mapToLong(AgentRun::getDurationMs).sum() /
                                    Math.max(1, userRuns.stream().filter(r -> r.getDurationMs() != null).count())
                    );
                })
                .sorted(Comparator.comparingLong(ByTeamResponse.TeamBreakdown::totalRuns).reversed())
                .toList();

        return new ByTeamResponse(team.getOrgId(), new AnalyticsSummaryResponse.PeriodRange(from, to), breakdowns);
    }

    public RunListResponse getUserRuns(UUID userId, String from, String to,
                                        String agentType, String status, int limit) {
        Instant fromInstant = parseFrom(from);
        Instant toInstant = parseTo(to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, fromInstant, toInstant, agentType, status);

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
                        r.getTotalCost().setScale(6, RoundingMode.HALF_UP).toPlainString()
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
                r.getInputCost().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                r.getOutputCost().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                r.getTotalCost().setScale(6, RoundingMode.HALF_UP).toPlainString(),
                r.getErrorCategory(), r.getErrorMessage()
        );
    }

    // --- Private helpers ---

    private AnalyticsSummaryResponse buildSummary(UUID orgId, String from, String to, List<AgentRun> runs) {
        long total = runs.size();
        long succeeded = runs.stream().filter(r -> "SUCCEEDED".equals(r.getStatus())).count();
        long failed = runs.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
        long cancelled = runs.stream().filter(r -> "CANCELLED".equals(r.getStatus())).count();
        long running = runs.stream().filter(r -> "RUNNING".equals(r.getStatus())).count();
        double successRate = total > 0 ? (double) succeeded / total : 0;
        long totalTokens = runs.stream().mapToLong(AgentRun::getTotalTokens).sum();
        long inputTokens = runs.stream().mapToLong(AgentRun::getInputTokens).sum();
        long outputTokens = runs.stream().mapToLong(AgentRun::getOutputTokens).sum();
        BigDecimal totalCost = runs.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Long> durations = runs.stream()
                .filter(r -> r.getDurationMs() != null)
                .map(AgentRun::getDurationMs)
                .sorted()
                .toList();

        long avg = durations.isEmpty() ? 0 : durations.stream().mapToLong(Long::longValue).sum() / durations.size();
        long p50 = percentile(durations, 50);
        long p95 = percentile(durations, 95);
        long p99 = percentile(durations, 99);

        return new AnalyticsSummaryResponse(
                orgId,
                new AnalyticsSummaryResponse.PeriodRange(from, to),
                total, succeeded, failed, cancelled, running,
                Math.round(successRate * 10000.0) / 10000.0,
                totalTokens, inputTokens, outputTokens,
                totalCost.setScale(6, RoundingMode.HALF_UP).toPlainString(),
                avg, p50, p95, p99
        );
    }

    private TimeseriesResponse buildTimeseries(UUID orgId, String granularity, List<AgentRun> runs) {
        Map<String, List<AgentRun>> grouped = runs.stream()
                .collect(Collectors.groupingBy(r ->
                        r.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate().toString()
                ));

        List<TimeseriesResponse.DataPoint> points = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<AgentRun> dayRuns = e.getValue();
                    long succeeded = dayRuns.stream().filter(r -> "SUCCEEDED".equals(r.getStatus())).count();
                    long failed = dayRuns.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
                    long totalTokens = dayRuns.stream().mapToLong(AgentRun::getTotalTokens).sum();
                    BigDecimal totalCost = dayRuns.stream().map(AgentRun::getTotalCost).reduce(BigDecimal.ZERO, BigDecimal::add);
                    long avgDuration = dayRuns.stream().filter(r -> r.getDurationMs() != null)
                            .mapToLong(AgentRun::getDurationMs).sum() /
                            Math.max(1, dayRuns.stream().filter(r -> r.getDurationMs() != null).count());
                    return new TimeseriesResponse.DataPoint(
                            e.getKey() + "T00:00:00Z",
                            dayRuns.size(), succeeded, failed,
                            totalTokens,
                            totalCost.setScale(6, RoundingMode.HALF_UP).toPlainString(),
                            avgDuration
                    );
                })
                .toList();

        return new TimeseriesResponse(orgId, granularity, points);
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted.isEmpty()) return 0;
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private Instant parseFrom(String date) {
        return LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant parseTo(String date) {
        return LocalDate.parse(date).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
