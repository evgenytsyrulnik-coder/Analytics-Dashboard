package com.analytics.dashboard.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-EP-001 through IT-EP-005: Authentication endpoint integration tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest extends ApiIntegrationTest {

    // IT-EP-001: Valid login returns 200 with token, userId, orgId, role, teams
    @Test
    void validLogin_returns200WithTokenAndUserDetails() {
        Map<String, String> body = Map.of("email", "admin@acme.com", "password", PASSWORD);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> respBody = response.getBody();
        assertNotNull(respBody);
        assertNotNull(respBody.get("token"));
        assertTrue(((String) respBody.get("token")).length() > 10);
        assertEquals(ACME_ADMIN_USER_ID.toString(), respBody.get("userId").toString());
        assertEquals(ACME_ORG_ID.toString(), respBody.get("orgId").toString());
        assertEquals("ORG_ADMIN", respBody.get("role"));
        assertEquals("admin@acme.com", respBody.get("email"));
        assertEquals("Alice Chen", respBody.get("displayName"));
        assertNotNull(respBody.get("teams"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teams = (List<Map<String, Object>>) respBody.get("teams");
        assertFalse(teams.isEmpty(), "Admin should have at least one team");
    }

    // IT-EP-002: Invalid password returns 401
    @Test
    void invalidPassword_returns401() {
        Map<String, String> body = Map.of("email", "admin@acme.com", "password", "wrong-password");
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        Map<String, Object> respBody = response.getBody();
        assertNotNull(respBody);
        assertEquals(401, respBody.get("status"));
    }

    // IT-EP-003: Non-existent email returns 401
    @Test
    void nonExistentEmail_returns401() {
        Map<String, String> body = Map.of("email", "nobody@acme.com", "password", PASSWORD);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // IT-EP-004: Protected endpoint without JWT returns 401
    @Test
    void protectedEndpointWithoutJwt_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO,
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // IT-EP-005: Protected endpoint with expired JWT returns 401
    @Test
    void protectedEndpointWithExpiredJwt_returns401() {
        // Generate an expired token by manually building one with a past expiration
        String secret = "test-secret-key-minimum-256-bits-long-for-hs256-algorithm-testing";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String expiredToken = Jwts.builder()
                .subject(ACME_ADMIN_USER_ID.toString())
                .claim("org_id", ACME_ORG_ID.toString())
                .claim("roles", List.of("ORG_ADMIN"))
                .claim("teams", List.of(ACME_PLATFORM_TEAM_ID.toString()))
                .issuedAt(new Date(System.currentTimeMillis() - 200000))
                .expiration(new Date(System.currentTimeMillis() - 100000)) // expired 100s ago
                .signWith(key)
                .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expiredToken);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
}
