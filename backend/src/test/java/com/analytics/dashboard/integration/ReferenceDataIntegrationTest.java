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
 * IT-EP-042 through IT-EP-046: Reference data endpoint integration tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReferenceDataIntegrationTest extends ApiIntegrationTest {

    private String adminToken;
    private String memberToken;
    private String globexAdminToken;

    @BeforeAll
    void setUp() {
        adminToken = loginAndGetToken("admin@acme.com", PASSWORD);
        memberToken = loginAndGetToken("member1@acme.com", PASSWORD);
        globexAdminToken = loginAndGetToken("admin2@globex.com", PASSWORD);
    }

    // IT-EP-042: GET /orgs/{orgId}/teams -> 200 with teams list
    @Test
    void getTeams_returns200WithTeamsList() {
        // Teams endpoint has no role restriction, just org validation
        ResponseEntity<Map<String, Object>> response = authenticatedGet(memberToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/teams");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> teams = (List<Map<String, Object>>) body.get("teams");
        assertNotNull(teams);
        assertEquals(5, teams.size(), "Acme should have 5 teams");
        // Verify team structure
        Map<String, Object> firstTeam = teams.get(0);
        assertNotNull(firstTeam.get("team_id"));
        assertNotNull(firstTeam.get("name"));
    }

    // IT-EP-043: GET /orgs/{orgId}/agent-types -> 200 with agent types list
    @Test
    void getAgentTypes_returns200WithAgentTypesList() {
        // Agent-types endpoint has no role restriction, just org validation
        ResponseEntity<Map<String, Object>> response = authenticatedGet(memberToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/agent-types");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> agentTypes = (List<Map<String, Object>>) body.get("agent_types");
        assertNotNull(agentTypes);
        assertEquals(4, agentTypes.size(), "Acme should have 4 agent types");
        // Verify agent type structure
        Map<String, Object> first = agentTypes.get(0);
        assertNotNull(first.get("slug"));
        assertNotNull(first.get("display_name"));
    }

    // IT-EP-044: GET /orgs/{orgId}/users (ORG_ADMIN) -> 200 with users
    @Test
    void orgAdminGetsUsers_returns200WithUsersList() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/users");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) body.get("users");
        assertNotNull(users);
        // Acme: 1 admin + 5 leads + 20 members = 26 users
        assertEquals(26, users.size(), "Acme should have 26 users");
        // Verify user structure
        Map<String, Object> firstUser = users.get(0);
        assertNotNull(firstUser.get("user_id"));
        assertNotNull(firstUser.get("display_name"));
        assertNotNull(firstUser.get("email"));
    }

    // IT-EP-045: GET /orgs/{orgId}/users wrong org -> 403
    @Test
    void orgAdminGetsUsersWrongOrg_returns403() {
        // Acme admin tries to list Globex users
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + GLOBEX_ORG_ID + "/users");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // IT-EP-046: GET /orgs/{orgId}/budgets -> 200
    @Test
    void orgAdminGetsBudgets_returns200() {
        ResponseEntity<Map<String, Object>> response = authenticatedGet(adminToken,
                "/api/v1/orgs/" + ACME_ORG_ID + "/budgets");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> budgets = (List<Map<String, Object>>) body.get("budgets");
        assertNotNull(budgets);
        // Acme: 1 org budget + 5 team budgets = 6 budgets
        assertEquals(6, budgets.size(), "Acme should have 6 budgets (1 org + 5 teams)");
    }
}
