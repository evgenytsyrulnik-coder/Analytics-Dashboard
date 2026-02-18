package com.analytics.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record PagedRunListResponse(
    List<RunItem> runs,
    int page,
    int totalPages,
    long totalElements
) {
    public record RunItem(
        UUID runId,
        UUID userId,
        String userName,
        UUID teamId,
        String teamName,
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
