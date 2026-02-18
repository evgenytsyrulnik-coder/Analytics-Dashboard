package com.analytics.dashboard.integration;

import com.analytics.dashboard.entity.User;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-EP-026 through IT-EP-036: User-level analytics endpoint integration tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserAnalyticsIntegrationTest extends ApiIntegrationTest {

    private String adminToken;
    private String platformLeadToken;
    private String dataScienceLeadToken;
    private String member1Token;
    private String globexAdminToken;

    @BeforeAll
    void setUp() {
        adminToken = loginAndGetToken("admin@acme.com", PASSWORD);
        platformLeadToken = loginAndGetToken("lead-platform@acme.com", PASSWORD);
        dataScienceLeadToken = loginAndGetToken("lead-data-science@acme.com", PASSWORD);
        member1Token = loginAndGetToken("member1@acme.com", PASSWORD);
        globexAdminToken = loginAndGetToken("admin2@globex.com", PASSWORD);
    }

    // IT-EP-026: /users/me/analytics/summary -> 200 with personal metrics
    @Test
    void userGetsMySummary_returns200WithPersonalMetrics() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(member1Token,
                "/api/v1/users/me/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(ACME_MEMBER1_USER_ID.toString(), body.get("userId").toString());
        assertNotNull(body.get("displayName"));
        assertTrue(((Number) body.get("totalRuns")).longValue() > 0, "User should have runs");
        assertNotNull(body.get("totalCost"));
        assertNotNull(body.get("avgDurationMs"));
        assertTrue(((Number) body.get("teamRank")).intValue() > 0, "teamRank should be > 0");
        assertTrue(((Number) body.get("teamSize")).intValue() > 0, "teamSize should be > 0");
    }

    // IT-EP-027: /users/me/analytics/timeseries -> 200
    @Test
    void userGetsMyTimeseries_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(member1Token,
                "/api/v1/users/me/analytics/timeseries?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) body.get("dataPoints");
        assertNotNull(dataPoints);
        assertFalse(dataPoints.isEmpty(), "User should have timeseries data");
    }

    // IT-EP-028: /users/me/runs -> 200 with runs + hasMore
    @Test
    void userGetsMyRuns_returns200WithRunsAndHasMore() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(member1Token,
                "/api/v1/users/me/runs?from=" + DATE_FROM + "&to=" + DATE_TO + "&limit=10");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) body.get("runs");
        assertNotNull(runs);
        assertFalse(runs.isEmpty(), "User should have runs");
        assertTrue(runs.size() <= 10, "Should respect limit");
        assertNotNull(body.get("hasMore"));
        // With 4950 runs per user and limit=10, hasMore should be true
        assertTrue((Boolean) body.get("hasMore"), "hasMore should be true when limit < total runs");
    }

    // IT-EP-029: /users/me/runs limit capped at 200
    @Test
    void myRunsLimitCappedAt200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(member1Token,
                "/api/v1/users/me/runs?from=" + DATE_FROM + "&to=" + DATE_TO + "&limit=999");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) body.get("runs");
        assertNotNull(runs);
        assertTrue(runs.size() <= 200, "User runs limit should be capped at 200");
    }

    // IT-EP-030: ORG_ADMIN views any user -> 200
    @Test
    void orgAdminViewsAnyUser_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/users/" + ACME_MEMBER1_USER_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(ACME_MEMBER1_USER_ID.toString(), body.get("userId").toString());
        assertTrue(((Number) body.get("totalRuns")).longValue() > 0);
    }

    // IT-EP-031: TEAM_LEAD sharing team views user -> 200
    @Test
    void teamLeadSharingTeamViewsUser_returns200() {
        // member1@acme.com is member index 0, assigned to team index 0/4 = platform team
        // lead-platform@acme.com is the lead of the platform team
        ResponseEntity<Map<String, Object>> response = authenticatedGet(platformLeadToken,
                "/api/v1/users/" + ACME_MEMBER1_USER_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(ACME_MEMBER1_USER_ID.toString(), body.get("userId").toString());
    }

    // IT-EP-032: TEAM_LEAD not sharing team -> 403
    @Test
    void teamLeadNotSharingTeam_returns403() {
        // member5@acme.com is member index 4, assigned to team index 4/4 = data-science team (index 1)
        // Actually member index 4 => team index 4/4=1 => data-science
        // lead-platform only has access to platform team, not data-science
        UUID memberInDataScienceTeam = UUID.fromString("00000000-0000-0000-0000-000000000114");
        // member5 (index 4) is on platform team (4/4=1 => data-science team)
        // Let's use a member that is definitely on the data-science team
        // member index 4 => team index 4/4=1 (data-science), member index 5 => 5/4=1 (data-science)
        // member index 6 => 6/4=1 (data-science), member index 7 => 7/4=1 (data-science)
        // member5@acme.com = uuid(114), in data-science team
        ResponseEntity<Map<String, Object>> response = authenticatedGet(platformLeadToken,
                "/api/v1/users/" + memberInDataScienceTeam + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-033: User in different org -> 403
    @Test
    void userInDifferentOrg_returns403() {
        // Acme admin tries to view Globex user
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/users/" + GLOBEX_ADMIN_USER_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-034: Non-existent user -> 404
    @Test
    void nonExistentUser_returns404() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/users/" + NON_EXISTENT_ID + "/analytics/summary?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // IT-EP-035: User timeseries -> 200
    @Test
    void orgAdminViewsUserTimeseries_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/users/" + ACME_MEMBER1_USER_ID + "/analytics/timeseries?from=" + DATE_FROM + "&to=" + DATE_TO);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dataPoints = (List<Map<String, Object>>) body.get("dataPoints");
        assertNotNull(dataPoints);
        assertFalse(dataPoints.isEmpty());
    }

    // IT-EP-036: User runs -> 200
    @Test
    void orgAdminViewsUserRuns_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/users/" + ACME_MEMBER1_USER_ID + "/runs?from=" + DATE_FROM + "&to=" + DATE_TO + "&limit=10");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> runs = (List<Map<String, Object>>) body.get("runs");
        assertNotNull(runs);
        assertFalse(runs.isEmpty());
        assertTrue(runs.size() <= 10);
        assertNotNull(body.get("hasMore"));
    }
}
