package com.analytics.dashboard.integration;

import com.analytics.dashboard.entity.AgentRun;
import com.analytics.dashboard.entity.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-EP-037 through IT-EP-041: Run detail endpoint integration tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RunDetailIntegrationTest extends ApiIntegrationTest {

    private String adminToken;
    private String platformLeadToken;
    private String member1Token;
    private String dataScienceLeadToken;

    // We will find actual run IDs from the database during setup
    private UUID member1RunId;
    private UUID member1RunTeamId;

    @BeforeAll
    void setUp() {
        adminToken = loginAndGetToken("admin@acme.com", PASSWORD);
        platformLeadToken = loginAndGetToken("lead-platform@acme.com", PASSWORD);
        dataScienceLeadToken = loginAndGetToken("lead-data-science@acme.com", PASSWORD);
        member1Token = loginAndGetToken("member1@acme.com", PASSWORD);

        // Find a run belonging to member1 (on the platform team)
        Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant from = Instant.now().minus(91, ChronoUnit.DAYS);
        List<AgentRun> runs = agentRunRepository.findByUserIdAndStartedAtBetween(
                ACME_MEMBER1_USER_ID, from, to);
        assertFalse(runs.isEmpty(), "member1 should have agent runs in the seeded data");
        AgentRun run = runs.get(0);
        member1RunId = run.getId();
        member1RunTeamId = run.getTeamId();
    }

    // IT-EP-037: Run owner gets detail -> 200 with full run detail
    @Test
    void runOwnerGetsDetail_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(member1Token,
                "/api/v1/runs/" + member1RunId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(member1RunId.toString(), body.get("runId").toString());
        assertEquals(ACME_MEMBER1_USER_ID.toString(), body.get("userId").toString());
        assertNotNull(body.get("agentType"));
        assertNotNull(body.get("modelName"));
        assertNotNull(body.get("modelVersion"));
        assertNotNull(body.get("status"));
        assertNotNull(body.get("startedAt"));
        assertNotNull(body.get("totalTokens"));
        assertNotNull(body.get("inputTokens"));
        assertNotNull(body.get("outputTokens"));
        assertNotNull(body.get("totalCost"));
        assertNotNull(body.get("inputCost"));
        assertNotNull(body.get("outputCost"));
        assertNotNull(body.get("durationMs"));
    }

    // IT-EP-038: ORG_ADMIN (non-owner) -> 200
    @Test
    void orgAdminNonOwner_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/runs/" + member1RunId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(member1RunId.toString(), body.get("runId").toString());
    }

    // IT-EP-039: TEAM_LEAD with team access -> 200
    @Test
    void teamLeadWithTeamAccess_returns200() {
        // member1 is in the platform team, lead-platform is the lead
        ResponseEntity<Map<String, Object>> response = authenticatedGet(platformLeadToken,
                "/api/v1/runs/" + member1RunId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(member1RunId.toString(), body.get("runId").toString());
    }

    // IT-EP-040: Non-owner, non-admin, no team access -> 403
    @Test
    void nonOwnerNonAdminNoTeamAccess_returns403() {
        // data-science lead does not have access to platform team member1's runs
        ResponseEntity<Map<String, Object>> response = authenticatedGet(dataScienceLeadToken,
                "/api/v1/runs/" + member1RunId);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-041: Non-existent run -> 404
    @Test
    void nonExistentRun_returns404() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/runs/" + NON_EXISTENT_ID);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
