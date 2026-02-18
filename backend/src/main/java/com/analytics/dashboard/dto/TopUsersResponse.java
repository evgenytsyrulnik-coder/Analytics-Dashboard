package com.analytics.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record TopUsersResponse(
    UUID orgId,
    String sortBy,
    List<UserMetric> users
) {
    public record UserMetric(
        UUID userId,
        String displayName,
        String email,
        String teamName,
        long totalRuns,
        long totalTokens,
        String totalCost
    ) {}
}
