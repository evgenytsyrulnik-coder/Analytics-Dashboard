package com.analytics.dashboard.repository;

import com.analytics.dashboard.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    List<Budget> findByOrgId(UUID orgId);
}
