package com.analytics.dashboard.service;

import com.analytics.dashboard.dto.AnalyticsSummaryResponse;
import com.analytics.dashboard.dto.ByTeamResponse;
import com.analytics.dashboard.dto.TimeseriesResponse;
import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.repository.UserRepository;
import com.analytics.dashboard.service.RunAggregator.DateRange;
import com.analytics.dashboard.service.RunAggregator.RunAggregates;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TeamAnalyticsService {

    private final AgentRunRepository agentRunRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    public TeamAnalyticsService(AgentRunRepository agentRunRepository,
                                TeamRepository teamRepository,
                                UserRepository userRepository) {
        this.agentRunRepository = agentRunRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
    }

    public AnalyticsSummaryResponse getTeamSummary(UUID teamId, String from, String to,
                                                    String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, range.from(), range.to(), agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();
        return RunAggregator.buildSummary(team.getOrgId(), from, to, runs);
    }

    public TimeseriesResponse getTeamTimeseries(UUID teamId, String from, String to,
                                                 String agentType, String status, String granularity) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findTeamFiltered(teamId, range.from(), range.to(), agentType, status);
        Team team = teamRepository.findById(teamId).orElseThrow();
        return RunAggregator.buildTimeseries(team.getOrgId(), granularity != null ? granularity : "DAILY", runs);
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
}
