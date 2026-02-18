package com.analytics.dashboard.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import javax.crypto.SecretKey;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-EP-001 through IT-EP-005: Authentication endpoint integration tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthIntegrationTest extends ApiIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    /**
     * Makes a raw POST request to the login endpoint without going through the HTTP proxy.
     * This avoids the HttpRetryException that occurs when the JVM proxy intercepts 401 responses.
     * Returns the HTTP status code.
     */
    private int rawLoginPost(String email, String password) throws Exception {
        URI uri = new URI("http://localhost:" + port + "/api/v1/auth/login");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection(java.net.Proxy.NO_PROXY);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        // Disable auto-redirect/auth following to prevent HttpRetryException
        conn.setInstanceFollowRedirects(false);

        String json = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }

        return conn.getResponseCode();
    }

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
    void invalidPassword_returns401() throws Exception {
        int statusCode = rawLoginPost("admin@acme.com", "wrong-password");
        assertEquals(401, statusCode);
    }

    // IT-EP-003: Non-existent email returns 401
    @Test
    void nonExistentEmail_returns401() throws Exception {
        int statusCode = rawLoginPost("nobody@acme.com", PASSWORD);
        assertEquals(401, statusCode);
    }

    // IT-EP-004: Protected endpoint without JWT returns 403
    // Spring Security with STATELESS sessions and no custom AuthenticationEntryPoint
    // returns 403 (Forbidden) for unauthenticated requests to protected endpoints.
    @Test
    void protectedEndpointWithoutJwt_returns403() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO,
                String.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-005: Protected endpoint with expired JWT returns 403
    // An expired token fails parsing, so no Authentication is set in the SecurityContext.
    // Spring Security treats this the same as an unauthenticated request -> 403.
    @Test
    void protectedEndpointWithExpiredJwt_returns403() {
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

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
