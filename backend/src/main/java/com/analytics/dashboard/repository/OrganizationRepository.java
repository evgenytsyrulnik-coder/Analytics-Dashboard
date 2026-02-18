package com.analytics.dashboard.repository;

import com.analytics.dashboard.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
