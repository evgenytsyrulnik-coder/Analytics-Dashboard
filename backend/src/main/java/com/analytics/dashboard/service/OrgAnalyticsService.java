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
import com.analytics.dashboard.service.RunAggregator.DateRange;
import com.analytics.dashboard.service.RunAggregator.RunAggregates;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrgAnalyticsService {

    private final AgentRunRepository agentRunRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AgentTypeRepository agentTypeRepository;

    public OrgAnalyticsService(AgentRunRepository agentRunRepository,
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
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), teamId, agentType, status);
        return RunAggregator.buildSummary(orgId, from, to, runs);
    }

    public TimeseriesResponse getOrgTimeseries(UUID orgId, String from, String to,
                                                UUID teamId, String agentType, String status, String granularity) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), teamId, agentType, status);
        return RunAggregator.buildTimeseries(orgId, granularity != null ? granularity : "DAILY", runs);
    }

    public ByTeamResponse getByTeam(UUID orgId, String from, String to, String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findFiltered(orgId, range.from(), range.to(), null, agentType, status);

        Map<UUID, Team> teams = teamRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));

        List<ByTeamResponse.TeamBreakdown> breakdowns = RunAggregator.groupByNonNullKey(runs, AgentRun::getTeamId)
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
                            RunAggregator.formatCost(RunAggregator.sumCost(userRuns))
                    );
                })
                .sorted(comp)
                .limit(limit)
                .toList();

        return new TopUsersResponse(orgId, effectiveSortBy, userMetrics);
    }

    public PagedRunListResponse getOrgRuns(UUID orgId, String from, String to,
                                             UUID teamId, UUID userId, List<String> statuses,
                                             String agentType, int page, int size) {
        DateRange range = DateRange.of(from, to);
        boolean filterByStatus = statuses != null && !statuses.isEmpty();
        Page<AgentRun> result = agentRunRepository.findOrgFilteredPaged(
                orgId, range.from(), range.to(), teamId, userId, agentType,
                filterByStatus, filterByStatus ? statuses : List.of(),
                PageRequest.of(page, size));

        Map<UUID, User> users = userRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<UUID, Team> teams = teamRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(Team::getId, t -> t));
        Map<String, AgentType> types = agentTypeRepository.findByOrgId(orgId).stream()
                .collect(Collectors.toMap(AgentType::getSlug, t -> t));

        List<PagedRunListResponse.RunItem> items = result.getContent().stream()
                .map(r -> {
                    User u = users.get(r.getUserId());
                    Team t = r.getTeamId() != null ? teams.get(r.getTeamId()) : null;
                    AgentType at = types.get(r.getAgentTypeSlug());
                    return new PagedRunListResponse.RunItem(
                            r.getId(),
                            r.getUserId(),
                            u != null ? u.getDisplayName() : "Unknown",
                            r.getTeamId(),
                            t != null ? t.getName() : "Unknown",
                            r.getAgentTypeSlug(),
                            at != null ? at.getDisplayName() : r.getAgentTypeSlug(),
                            r.getStatus(),
                            r.getStartedAt().toString(),
                            r.getFinishedAt() != null ? r.getFinishedAt().toString() : null,
                            r.getDurationMs() != null ? r.getDurationMs() : 0,
                            r.getTotalTokens(),
                            RunAggregator.formatCost(r.getTotalCost())
                    );
                })
                .toList();

        return new PagedRunListResponse(items, page, result.getTotalPages(), result.getTotalElements());
    }
}
