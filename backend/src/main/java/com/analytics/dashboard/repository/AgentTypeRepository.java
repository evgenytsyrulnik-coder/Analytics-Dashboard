package com.analytics.dashboard.repository;

import com.analytics.dashboard.entity.AgentType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentTypeRepository extends JpaRepository<AgentType, UUID> {
    List<AgentType> findByOrgId(UUID orgId);
    Optional<AgentType> findByOrgIdAndSlug(UUID orgId, String slug);
}
