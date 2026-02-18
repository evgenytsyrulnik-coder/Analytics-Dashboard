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
 * IT-EP-021 through IT-EP-025: Team-level analytics endpoint integration tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TeamAnalyticsIntegrationTest extends ApiIntegrationTest {

    private String adminToken;
    private String platformLeadToken;
    private String dataScienceLeadToken;

    @BeforeAll
    void setUp() {
        adminToken = loginAndGetToken("admin@acme.com", PASSWORD);
        platformLeadToken = loginAndGetToken("lead-platform@acme.com", PASSWORD);
        dataScienceLeadToken = loginAndGetToken("lead-data-science@acme.com", PASSWORD);
    }

    // IT-EP-021: TEAM_LEAD gets own team summary -> 200
    @Test
    void teamLeadGetsOwnTeamSummary_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(platformLeadToken,
                "/api/v1/teams/" + ACME_PLATFORM_TEAM_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(((Number) body.get("totalRuns")).longValue() > 0, "Team should have runs");
        assertTrue(((Number) body.get("succeededRuns")).longValue() > 0);
        assertTrue(((Number) body.get("totalTokens")).longValue() > 0);
        assertNotNull(body.get("totalCost"));
        assertNotNull(body.get("successRate"));
    }

    // IT-EP-022: TEAM_LEAD gets other team -> 403
    @Test
    void teamLeadGetsOtherTeam_returns403() {
        // Platform lead tries to access Data Science team
        ResponseEntity<Map<String, Object>> response = authenticatedGet(platformLeadToken,
                "/api/v1/teams/" + ACME_DATA_SCIENCE_TEAM_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-023: ORG_ADMIN gets any team -> 200
    @Test
    void orgAdminGetsAnyTeam_returns200() {
        // Admin can access any team within the org
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/teams/" + ACME_DATA_SCIENCE_TEAM_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(((Number) body.get("totalRuns")).longValue() > 0);
    }

    // IT-EP-024: Team timeseries -> 200
    @Test
    void teamLeadGetsTimeseries_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(platformLeadToken,
                "/api/v1/teams/" + ACME_PLATFORM_TEAM_ID + "/analytics/timeseries?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertNotNull(body.get("granularity"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) body.get("dataPoints");
        assertNotNull(dataPoints);
        assertFalse(dataPoints.isEmpty(), "Timeseries should have data points for the team");
    }

    // IT-EP-025: Team by-user -> 200
    @Test
    void teamLeadGetsByUser_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(platformLeadToken,
                "/api/v1/teams/" + ACME_PLATFORM_TEAM_ID + "/analytics/by-user?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teams = (List<Map<String, Object>>) body.get("teams");
        assertNotNull(teams);
        assertFalse(teams.isEmpty(), "Should have user breakdowns for the team");
        // Each entry should have user-level metrics
        Map<String, Object> firstEntry = teams.get(0);
        assertNotNull(firstEntry.get("teamId")); // reused field, holds userId in by-user response
        assertNotNull(firstEntry.get("teamName")); // reused field, holds displayName
        assertTrue(((Number) firstEntry.get("totalRuns")).longValue() > 0);
    }
}
