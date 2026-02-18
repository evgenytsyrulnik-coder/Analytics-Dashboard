package com.analytics.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record RunListResponse(
    List<RunSummary> runs,
    String nextCursor,
    boolean hasMore
) {
    public record RunSummary(
        UUID runId,
        String agentType,
        String agentTypeDisplayName,
        String status,
        String startedAt,
        String finishedAt,
        long durationMs,
        long totalTokens,
        String totalCost
    ) {}
}
