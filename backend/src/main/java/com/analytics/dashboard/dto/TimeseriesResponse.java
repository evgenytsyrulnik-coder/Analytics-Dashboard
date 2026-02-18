package com.analytics.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record TimeseriesResponse(
    UUID orgId,
    String granularity,
    List<DataPoint> dataPoints
) {
    public record DataPoint(
        String timestamp,
        long totalRuns,
        long succeededRuns,
        long failedRuns,
        long totalTokens,
        String totalCost,
        long avgDurationMs
    ) {}
}
