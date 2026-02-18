package com.analytics.dashboard.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private static final String SECRET = "test-secret-key-minimum-256-bits-long-for-hs256-algorithm-testing";
    private static final long EXPIRATION_MS = 86400000L;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateTokenReturnsNonNullString() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "ORG_ADMIN", List.of(UUID.randomUUID()));

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void parseTokenReturnsValidClaims() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "ORG_ADMIN", List.of(teamId));

        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void getUserIdExtractsCorrectUserId() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "ORG_ADMIN", List.of());
        Claims claims = jwtUtil.parseToken(token);

        UUID result = jwtUtil.getUserId(claims);

        assertThat(result).isEqualTo(userId);
    }

    @Test
    void getOrgIdExtractsCorrectOrgId() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "ORG_ADMIN", List.of());
        Claims claims = jwtUtil.parseToken(token);

        UUID result = jwtUtil.getOrgId(claims);

        assertThat(result).isEqualTo(orgId);
    }

    @Test
    void getRolesExtractsCorrectRoles() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "TEAM_LEAD", List.of());
        Claims claims = jwtUtil.parseToken(token);

        List<String> roles = jwtUtil.getRoles(claims);

        assertThat(roles).containsExactly("TEAM_LEAD");
    }

    @Test
    void getTeamsExtractsCorrectTeamIds() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID teamId1 = UUID.randomUUID();
        UUID teamId2 = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "MEMBER", List.of(teamId1, teamId2));
        Claims claims = jwtUtil.parseToken(token);

        List<String> teams = jwtUtil.getTeams(claims);

        assertThat(teams).containsExactly(teamId1.toString(), teamId2.toString());
    }

    @Test
    void parseTokenWithInvalidTokenThrowsException() {
        assertThatThrownBy(() -> jwtUtil.parseToken("invalid.token.here"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void parseTokenWithDifferentSecretThrowsSignatureException() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "MEMBER", List.of());

        JwtUtil otherJwtUtil = new JwtUtil(
                "different-secret-key-minimum-256-bits-long-for-hs256-algorithm-test", EXPIRATION_MS);

        assertThatThrownBy(() -> otherJwtUtil.parseToken(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void parseTokenWithExpiredTokenThrowsException() {
        JwtUtil expiredJwtUtil = new JwtUtil(SECRET, -1000L);
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = expiredJwtUtil.generateToken(userId, orgId, "MEMBER", List.of());

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void generateTokenWithEmptyTeamList() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "MEMBER", List.of());
        Claims claims = jwtUtil.parseToken(token);

        List<String> teams = jwtUtil.getTeams(claims);

        assertThat(teams).isEmpty();
    }

    @Test
    void tokenContainsIssuedAtAndExpiration() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, orgId, "MEMBER", List.of());
        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}
