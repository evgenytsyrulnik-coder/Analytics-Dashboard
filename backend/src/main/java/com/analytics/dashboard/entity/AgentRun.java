package com.analytics.dashboard.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_runs")
public class AgentRun {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "agent_type_slug", nullable = false)
    private String agentTypeSlug;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "input_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal inputCost = BigDecimal.ZERO;

    @Column(name = "output_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal outputCost = BigDecimal.ZERO;

    @Column(name = "total_cost", nullable = false, precision = 18, scale = 6)
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "error_category")
    private String errorCategory;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AgentRun() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrgId() { return orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getAgentTypeSlug() { return agentTypeSlug; }
    public void setAgentTypeSlug(String agentTypeSlug) { this.agentTypeSlug = agentTypeSlug; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
    public long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(long totalTokens) { this.totalTokens = totalTokens; }
    public BigDecimal getInputCost() { return inputCost; }
    public void setInputCost(BigDecimal inputCost) { this.inputCost = inputCost; }
    public BigDecimal getOutputCost() { return outputCost; }
    public void setOutputCost(BigDecimal outputCost) { this.outputCost = outputCost; }
    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }
    public String getErrorCategory() { return errorCategory; }
    public void setErrorCategory(String errorCategory) { this.errorCategory = errorCategory; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
