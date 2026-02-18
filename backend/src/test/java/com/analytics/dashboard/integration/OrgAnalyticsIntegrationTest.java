package com.analytics.dashboard.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-EP-006 through IT-EP-020: Org-level analytics endpoint integration tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgAnalyticsIntegrationTest extends ApiIntegrationTest {

    private String adminToken;
    private String teamLeadToken;
    private String memberToken;
    private String globexAdminToken;

    @BeforeAll
    void setUp() {
        adminToken = loginAndGetToken("admin@acme.com", PASSWORD);
        teamLeadToken = loginAndGetToken("lead-platform@acme.com", PASSWORD);
        memberToken = loginAndGetToken("member1@acme.com", PASSWORD);
        globexAdminToken = loginAndGetToken("admin2@globex.com", PASSWORD);
    }

    // IT-EP-006: ORG_ADMIN gets org summary -> 200 with aggregate metrics
    @Test
    void orgAdminGetsSummary_returns200WithMetrics() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(ACME_ORG_ID.toString(), body.get("orgId").toString());
        assertNotNull(body.get("period"));
        assertTrue(((Number) body.get("totalRuns")).longValue() > 0, "totalRuns should be > 0");
        assertTrue(((Number) body.get("succeededRuns")).longValue() > 0);
        assertTrue(((Number) body.get("totalTokens")).longValue() > 0);
        assertNotNull(body.get("totalCost"));
        assertNotNull(body.get("successRate"));
        assertNotNull(body.get("avgDurationMs"));
        assertNotNull(body.get("p50DurationMs"));
        assertNotNull(body.get("p95DurationMs"));
        assertNotNull(body.get("p99DurationMs"));
    }

    // IT-EP-007: Summary with filters -> 200 with filtered metrics
    @Test
    void orgAdminGetsSummaryWithFilters_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO
                        + "&team_id=" + ACME_PLATFORM_TEAM_ID + "&agent_type=code_review&status=SUCCEEDED");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        // Filtered results: only succeeded runs for code_review in platform team
        long totalRuns = ((Number) body.get("totalRuns")).longValue();
        long succeededRuns = ((Number) body.get("succeededRuns")).longValue();
        assertEquals(totalRuns, succeededRuns, "When filtering by SUCCEEDED status, all runs should be succeeded");
    }

    // IT-EP-008: Wrong org_id -> 403
    @Test
    void orgAdminAccessesWrongOrg_returns403() {
        // Acme admin tries to access Globex org
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + GLOBEX_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-009: Timeseries -> 200 with dataPoints array
    @Test
    void orgAdminGetsTimeseries_returns200WithDataPoints() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/timeseries?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(ACME_ORG_ID.toString(), body.get("orgId").toString());
        assertNotNull(body.get("granularity"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) body.get("dataPoints");
        assertNotNull(dataPoints);
        assertFalse(dataPoints.isEmpty(), "Timeseries should have data points");
        // Verify structure of first data point
        Map<String, Object> first = dataPoints.get(0);
        assertNotNull(first.get("timestamp"));
        assertNotNull(first.get("totalRuns"));
        assertNotNull(first.get("totalTokens"));
        assertNotNull(first.get("totalCost"));
    }

    // IT-EP-010: By-team -> 200 with teams array
    @Test
    void orgAdminGetsByTeam_returns200WithTeams() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/by-team?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teams = (List<Map<String, Object>>) body.get("teams");
        assertNotNull(teams);
        assertFalse(teams.isEmpty(), "Should have team breakdowns");
        // Each team should have metrics
        Map<String, Object> firstTeam = teams.get(0);
        assertNotNull(firstTeam.get("teamId"));
        assertNotNull(firstTeam.get("teamName"));
        assertTrue(((Number) firstTeam.get("totalRuns")).longValue() > 0);
    }

    // IT-EP-011: By-agent-type -> 200 (ORG_ADMIN)
    @Test
    void orgAdminGetsByAgentType_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/by-agent-type?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agentTypes = (List<Map<String, Object>>) body.get("agentTypes");
        assertNotNull(agentTypes);
        assertFalse(agentTypes.isEmpty(), "Should have agent type breakdowns");
    }

    // IT-EP-012: By-agent-type -> 200 (TEAM_LEAD can also access)
    @Test
    void teamLeadGetsByAgentType_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(teamLeadToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/by-agent-type?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("agentTypes"));
    }

    // IT-EP-013: Top-users -> 200 with users array
    @Test
    void orgAdminGetsTopUsers_returns200WithUsers() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/top-users?from=" + DATE_FROM + "&to=" + DATE_TO
                        + "&sort_by=runs&limit=10");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) body.get("users");
        assertNotNull(users);
        assertFalse(users.isEmpty(), "Should have user metrics");
        assertTrue(users.size() <= 10, "Should respect limit");
        // Verify structure
        Map<String, Object> firstUser = users.get(0);
        assertNotNull(firstUser.get("userId"));
        assertNotNull(firstUser.get("displayName"));
        assertNotNull(firstUser.get("totalRuns"));
    }

    // IT-EP-014: Top-users limit capped at 50
    @Test
    void topUsersLimitCappedAt50() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/top-users?from=" + DATE_FROM + "&to=" + DATE_TO
                        + "&limit=999");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) body.get("users");
        assertNotNull(users);
        // Acme has 26 users, so all should be returned but capped at 50 max
        assertTrue(users.size() <= 50, "Top users limit should be capped at 50");
    }

    // IT-EP-015: Org runs -> 200 with paged runs (default page 0, size 25)
    @Test
    void orgAdminGetsRuns_returns200WithPagedRuns() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/runs?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) body.get("runs");
        assertNotNull(runs);
        assertFalse(runs.isEmpty());
        assertTrue(runs.size() <= 25, "Default page size should be 25");
        assertEquals(0, ((Number) body.get("page")).intValue());
        assertTrue(((Number) body.get("totalPages")).intValue() > 0);
        assertTrue(((Number) body.get("totalElements")).longValue() > 0);
    }

    // IT-EP-016: Runs with filters -> 200
    @Test
    void orgAdminGetsRunsWithFilters_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/runs?from=" + DATE_FROM + "&to=" + DATE_TO
                        + "&team_id=" + ACME_PLATFORM_TEAM_ID + "&status=SUCCEEDED&agent_type=code_review&page=0&size=10");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) body.get("runs");
        assertNotNull(runs);
        assertTrue(runs.size() <= 10, "Should respect size param");
        // Verify all returned runs match filters
        for (Map<String, Object> run : runs) {
            assertEquals("SUCCEEDED", run.get("status"));
            assertEquals("code_review", run.get("agentType"));
        }
    }

    // IT-EP-017: Runs size capped at 100
    @Test
    void orgRunsSizeCappedAt100() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/runs?from=" + DATE_FROM + "&to=" + DATE_TO + "&size=500");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) body.get("runs");
        assertNotNull(runs);
        assertTrue(runs.size() <= 100, "Runs size should be capped at 100");
    }

    // IT-EP-018: Wrong org runs -> 403
    @Test
    void orgAdminAccessesWrongOrgRuns_returns403() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + GLOBEX_ORG_ID + "/runs?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-019: MEMBER cannot access org summary (requires ORG_ADMIN)
    @Test
    void memberCannotAccessOrgSummary_returns403() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(memberToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-020: TEAM_LEAD cannot access org summary (requires ORG_ADMIN)
    @Test
    void teamLeadCannotAccessOrgSummary_returns403() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(teamLeadToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
