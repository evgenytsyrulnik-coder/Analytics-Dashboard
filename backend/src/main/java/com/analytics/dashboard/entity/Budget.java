package com.analytics.dashboard.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String scope;

    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    @Column(name = "monthly_limit", nullable = false, precision = 18, scale = 6)
    private BigDecimal monthlyLimit;

    @Column(nullable = false)
    private String thresholds;

    @Column(name = "notification_channels", nullable = false)
    private String notificationChannels;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Budget() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrgId() { return orgId; }
    public void setOrgId(UUID orgId) { this.orgId = orgId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public UUID getScopeId() { return scopeId; }
    public void setScopeId(UUID scopeId) { this.scopeId = scopeId; }
    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public void setMonthlyLimit(BigDecimal monthlyLimit) { this.monthlyLimit = monthlyLimit; }
    public String getThresholds() { return thresholds; }
    public void setThresholds(String thresholds) { this.thresholds = thresholds; }
    public String getNotificationChannels() { return notificationChannels; }
    public void setNotificationChannels(String notificationChannels) { this.notificationChannels = notificationChannels; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
