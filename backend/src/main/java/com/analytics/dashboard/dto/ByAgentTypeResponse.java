package com.analytics.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record ByAgentTypeResponse(
    UUID orgId,
    AnalyticsSummaryResponse.PeriodRange period,
    List<AgentTypeBreakdown> agentTypes
) {
    public record AgentTypeBreakdown(
        String agentType,
        String displayName,
        long totalRuns,
        long totalTokens,
        String totalCost,
        double successRate,
        long avgDurationMs
    ) {}
}
