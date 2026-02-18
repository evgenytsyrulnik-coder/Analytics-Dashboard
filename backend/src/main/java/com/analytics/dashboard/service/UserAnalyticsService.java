package com.analytics.dashboard.service;

import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.AgentType;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.AgentTypeRepository;
import com.analytics.dashboard.repository.UserRepository;
import com.analytics.dashboard.service.RunAggregator.DateRange;
import com.analytics.dashboard.service.RunAggregator.RunAggregates;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserAnalyticsService {

    private final AgentRunRepository agentRunRepository;
    private final UserRepository userRepository;
    private final AgentTypeRepository agentTypeRepository;

    public UserAnalyticsService(AgentRunRepository agentRunRepository,
                                UserRepository userRepository,
                                AgentTypeRepository agentTypeRepository) {
        this.agentRunRepository = agentRunRepository;
        this.userRepository = userRepository;
        this.agentTypeRepository = agentTypeRepository;
    }

    public UserSummaryResponse getUserSummary(UUID userId, UUID orgId, String from, String to,
                                               String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, range.from(), range.to(), agentType, status);
        RunAggregates agg = RunAggregates.of(runs);

        String displayName = userRepository.findById(userId)
                .map(User::getDisplayName)
                .orElse("Unknown");

        List<AgentRun> orgRuns = agentRunRepository.findFiltered(orgId, range.from(), range.to(), null, null, null);
        int rank = RunAggregator.computeUserRank(userId, orgRuns);
        int teamSize = RunAggregator.countDistinctUsers(orgRuns);

        return new UserSummaryResponse(
                userId,
                displayName,
                new AnalyticsSummaryResponse.PeriodRange(from, to),
                agg.totalRuns(), agg.succeeded(), agg.failed(), agg.totalTokens(),
                agg.formattedCost(),
                agg.avgDurationMs(), rank, teamSize
        );
    }

    public TimeseriesResponse getUserTimeseries(UUID userId, String from, String to,
                                                 String agentType, String status) {
        DateRange range = DateRange.of(from, to);
        List<AgentRun> runs = agentRunRepository.findUserFiltered(userId, range.from(), range.to(), agentType, status);
        return RunAggregator.buildTimeseries(null, "DAILY", runs);
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
                        RunAggregator.formatCost(r.getTotalCost())
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
                RunAggregator.formatCost(r.getInputCost()),
                RunAggregator.formatCost(r.getOutputCost()),
                RunAggregator.formatCost(r.getTotalCost()),
                r.getErrorCategory(), r.getErrorMessage()
        );
    }
}
