package com.analytics.dashboard.config;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Helper to extract authenticated user info from the security context.
 */
@Component
public class AuthContext {

    private final JwtUtil jwtUtil;

    public AuthContext(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private Claims getClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Claims) auth.getPrincipal();
    }

    public UUID getUserId() {
        return jwtUtil.getUserId(getClaims());
    }

    public UUID getOrgId() {
        return jwtUtil.getOrgId(getClaims());
    }

    public List<String> getRoles() {
        return jwtUtil.getRoles(getClaims());
    }

    public List<UUID> getTeamIds() {
        return jwtUtil.getTeams(getClaims()).stream()
                .map(UUID::fromString)
                .toList();
    }

    public boolean isOrgAdmin() {
        return getRoles().contains("ORG_ADMIN");
    }

    public boolean isTeamLead() {
        return getRoles().contains("TEAM_LEAD");
    }

    public boolean hasTeamAccess(UUID teamId) {
        return isOrgAdmin() || getTeamIds().contains(teamId);
    }
}
