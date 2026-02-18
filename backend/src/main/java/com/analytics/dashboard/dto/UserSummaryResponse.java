package com.analytics.dashboard.dto;

import java.util.UUID;

public record UserSummaryResponse(
    UUID userId,
    String displayName,
    AnalyticsSummaryResponse.PeriodRange period,
    long totalRuns,
    long succeededRuns,
    long failedRuns,
    long totalTokens,
    String totalCost,
    long avgDurationMs,
    int teamRank,
    int teamSize
) {}
