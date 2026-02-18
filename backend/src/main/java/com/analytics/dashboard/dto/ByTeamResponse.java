package com.analytics.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record ByTeamResponse(
    UUID orgId,
    AnalyticsSummaryResponse.PeriodRange period,
    List<TeamBreakdown> teams
) {
    public record TeamBreakdown(
        UUID teamId,
        String teamName,
        long totalRuns,
        long totalTokens,
        String totalCost,
        double successRate,
        long avgDurationMs
    ) {}
}
