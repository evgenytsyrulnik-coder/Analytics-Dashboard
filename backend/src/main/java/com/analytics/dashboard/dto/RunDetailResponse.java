package com.analytics.dashboard.dto;

import java.util.UUID;

public record RunDetailResponse(
    UUID runId,
    UUID orgId,
    UUID teamId,
    UUID userId,
    String agentType,
    String agentTypeDisplayName,
    String modelName,
    String modelVersion,
    String status,
    String startedAt,
    String finishedAt,
    long durationMs,
    long inputTokens,
    long outputTokens,
    long totalTokens,
    String inputCost,
    String outputCost,
    String totalCost,
    String errorCategory,
    String errorMessage
) {}
