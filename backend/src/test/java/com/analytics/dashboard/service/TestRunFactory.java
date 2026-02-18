package com.analytics.dashboard.service;

import com.analytics.dashboard.entity.AgentRun;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Shared test helper for creating {@link AgentRun} fixtures.
 */
final class TestRunFactory {

    static final UUID ORG_ID = UUID.randomUUID();
    static final UUID TEAM_ID_1 = UUID.randomUUID();
    static final UUID TEAM_ID_2 = UUID.randomUUID();
    static final UUID USER_ID_1 = UUID.randomUUID();
    static final UUID USER_ID_2 = UUID.randomUUID();
    static final String FROM = "2025-01-01";
    static final String TO = "2025-01-31";

    private TestRunFactory() {}

    static AgentRun createRun(UUID id, UUID orgId, UUID teamId, UUID userId,
                               String status, long totalTokens, BigDecimal totalCost,
                               Long durationMs, String agentTypeSlug, Instant startedAt) {
        AgentRun run = new AgentRun();
        run.setId(id);
        run.setOrgId(orgId);
        run.setTeamId(teamId);
        run.setUserId(userId);
        run.setStatus(status);
        run.setInputTokens(totalTokens / 2);
        run.setOutputTokens(totalTokens / 2);
        run.setTotalTokens(totalTokens);
        run.setInputCost(totalCost.divide(BigDecimal.valueOf(2)));
        run.setOutputCost(totalCost.divide(BigDecimal.valueOf(2)));
        run.setTotalCost(totalCost);
        run.setDurationMs(durationMs);
        run.setAgentTypeSlug(agentTypeSlug != null ? agentTypeSlug : "code-review");
        run.setStartedAt(startedAt != null ? startedAt : Instant.parse("2025-01-15T10:00:00Z"));
        run.setFinishedAt(durationMs != null ? run.getStartedAt().plusMillis(durationMs) : null);
        run.setModelName("gpt-4");
        run.setModelVersion("v1");
        run.setCreatedAt(Instant.now());
        return run;
    }

    static AgentRun createSucceededRun(UUID teamId, UUID userId, long tokens, BigDecimal cost, long durationMs) {
        return createRun(UUID.randomUUID(), ORG_ID, teamId, userId, "SUCCEEDED",
                tokens, cost, durationMs, "code-review", Instant.parse("2025-01-15T10:00:00Z"));
    }

    static AgentRun createFailedRun(UUID teamId, UUID userId) {
        return createRun(UUID.randomUUID(), ORG_ID, teamId, userId, "FAILED",
                500L, new BigDecimal("0.05"), 2000L, "code-review", Instant.parse("2025-01-15T10:00:00Z"));
    }
}
