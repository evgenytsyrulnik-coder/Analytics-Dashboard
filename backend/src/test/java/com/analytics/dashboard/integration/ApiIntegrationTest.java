package com.analytics.dashboard.integration;

import com.analytics.dashboard.config.JwtUtil;
import com.analytics.dashboard.repository.AgentRunRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

/**
 * Base class for integration tests. Uses Spring Boot Test with a random port
 * and the H2 in-memory database auto-populated by the DataSeeder.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class ApiIntegrationTest {

    // Well-known org IDs from the DataSeeder
    protected static final UUID ACME_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    protected static final UUID GLOBEX_ORG_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    protected static final UUID NON_EXISTENT_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    // Well-known team IDs from the DataSeeder (uuid(10+i) for Acme, uuid(20+i) for Globex)
    protected static final UUID ACME_PLATFORM_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    protected static final UUID ACME_DATA_SCIENCE_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    protected static final UUID ACME_BACKEND_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");
    protected static final UUID GLOBEX_CLOUD_INFRA_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    // Well-known user IDs from the DataSeeder
    protected static final UUID ACME_ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
    protected static final UUID ACME_LEAD_PLATFORM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    protected static final UUID ACME_LEAD_DATA_SCIENCE_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    protected static final UUID ACME_MEMBER1_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000110");
    protected static final UUID GLOBEX_ADMIN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000200");
    protected static final UUID GLOBEX_ADMIN2_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");

    // Date range covering all seeded data (90 days back from now)
    protected static final String DATE_FROM = "2020-01-01";
    protected static final String DATE_TO = "2030-12-31";

    protected static final String PASSWORD = "password123";

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JwtUtil jwtUtil;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TeamRepository teamRepository;

    @Autowired
    protected AgentRunRepository agentRunRepository;

    /**
     * Logs in with the given credentials and returns the JWT token from the response.
     */
    protected String loginAndGetToken(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body),
                new ParameterizedTypeReference<>() {}
        );
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new IllegalStateException("Login failed for " + email + ": " + response.getStatusCode());
        }
        return (String) response.getBody().get("token");
    }

    /**
     * Creates HTTP headers with a Bearer token for authenticated requests.
     */
    protected HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * Convenience: performs a GET request with the given token and returns the response as a Map.
     */
    protected ResponseEntity<Map<String, Object>> authenticatedGet(String token, String url) {
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)),
                new ParameterizedTypeReference<>() {}
        );
    }

    /**
     * Convenience: performs a GET request without authentication.
     */
    protected ResponseEntity<Map<String, Object>> unauthenticatedGet(String url) {
        return restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
    }
}
