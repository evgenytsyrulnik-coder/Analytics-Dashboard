package com.analytics.dashboard.config;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Helper to extract authenticated user info from the security context.
 * Works in both password mode (JJWT Claims principal) and OIDC mode (Spring Jwt principal).
 */
@Component
public class AuthContext {

    private final JwtUtil jwtUtil;
    private final IdentityProviderConfig idpConfig;

    public AuthContext(JwtUtil jwtUtil, IdentityProviderConfig idpConfig) {
        this.jwtUtil = jwtUtil;
        this.idpConfig = idpConfig;
    }

    public UUID getUserId() {
        if (idpConfig.isOidc()) {
            String claim = idpConfig.getOidc().getClaims().getUserId();
            return UUID.fromString(getJwt().getClaimAsString(claim));
        }
        return jwtUtil.getUserId(getClaims());
    }

    public UUID getOrgId() {
        if (idpConfig.isOidc()) {
            String claim = idpConfig.getOidc().getClaims().getOrgId();
            return UUID.fromString(getJwt().getClaimAsString(claim));
        }
        return jwtUtil.getOrgId(getClaims());
    }

    public List<String> getRoles() {
        if (idpConfig.isOidc()) {
            String claim = idpConfig.getOidc().getClaims().getRoles();
            return getJwt().getClaimAsStringList(claim);
        }
        return jwtUtil.getRoles(getClaims());
    }

    public List<UUID> getTeamIds() {
        if (idpConfig.isOidc()) {
            String claim = idpConfig.getOidc().getClaims().getTeams();
            List<String> teams = getJwt().getClaimAsStringList(claim);
            return teams != null ? teams.stream().map(UUID::fromString).toList() : List.of();
        }
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

    private Claims getClaims() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Claims) auth.getPrincipal();
    }

    private Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Jwt) auth.getPrincipal();
    }
}
