package com.analytics.dashboard.repository;

import com.analytics.dashboard.entity.AgentRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AgentRunRepository extends JpaRepository<AgentRun, UUID> {

    List<AgentRun> findByOrgIdAndStartedAtBetween(UUID orgId, Instant from, Instant to);

    List<AgentRun> findByTeamIdAndStartedAtBetween(UUID teamId, Instant from, Instant to);

    List<AgentRun> findByUserIdAndStartedAtBetween(UUID userId, Instant from, Instant to);

    Page<AgentRun> findByUserIdAndStartedAtBetweenOrderByStartedAtDesc(
            UUID userId, Instant from, Instant to, Pageable pageable);

    @Query("SELECT ar FROM AgentRun ar WHERE ar.orgId = :orgId AND ar.startedAt BETWEEN :from AND :to " +
           "AND (:teamId IS NULL OR ar.teamId = :teamId) " +
           "AND (:agentType IS NULL OR ar.agentTypeSlug = :agentType) " +
           "AND (:status IS NULL OR ar.status = :status)")
    List<AgentRun> findFiltered(@Param("orgId") UUID orgId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                @Param("teamId") UUID teamId,
                                @Param("agentType") String agentType,
                                @Param("status") String status);

    @Query("SELECT ar FROM AgentRun ar WHERE ar.teamId = :teamId AND ar.startedAt BETWEEN :from AND :to " +
           "AND (:agentType IS NULL OR ar.agentTypeSlug = :agentType) " +
           "AND (:status IS NULL OR ar.status = :status)")
    List<AgentRun> findTeamFiltered(@Param("teamId") UUID teamId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    @Param("agentType") String agentType,
                                    @Param("status") String status);

    @Query("SELECT ar FROM AgentRun ar WHERE ar.userId = :userId AND ar.startedAt BETWEEN :from AND :to " +
           "AND (:agentType IS NULL OR ar.agentTypeSlug = :agentType) " +
           "AND (:status IS NULL OR ar.status = :status) " +
           "ORDER BY ar.startedAt DESC")
    List<AgentRun> findUserFiltered(@Param("userId") UUID userId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to,
                                    @Param("agentType") String agentType,
                                    @Param("status") String status);
}
