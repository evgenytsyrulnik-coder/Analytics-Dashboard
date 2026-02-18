package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.AuthContext;
import com.analytics.dashboard.dto.*;
import com.analytics.dashboard.entity.AgentType;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.AgentTypeRepository;
import com.analytics.dashboard.repository.BudgetRepository;
import com.analytics.dashboard.repository.TeamRepository;
import com.analytics.dashboard.repository.UserRepository;
import com.analytics.dashboard.service.OrgAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}")
public class OrgAnalyticsController {

    private final OrgAnalyticsService analyticsService;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AgentTypeRepository agentTypeRepository;
    private final BudgetRepository budgetRepository;
    private final AuthContext authContext;

    public OrgAnalyticsController(OrgAnalyticsService analyticsService,
                                   TeamRepository teamRepository,
                                   UserRepository userRepository,
                                   AgentTypeRepository agentTypeRepository,
                                   BudgetRepository budgetRepository,
                                   AuthContext authContext) {
        this.analyticsService = analyticsService;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.agentTypeRepository = agentTypeRepository;
        this.budgetRepository = budgetRepository;
        this.authContext = authContext;
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> getSummary(@PathVariable UUID orgId,
                                         @RequestParam String from,
                                         @RequestParam String to,
                                         @RequestParam(required = false) UUID team_id,
                                         @RequestParam(required = false) String agent_type,
                                         @RequestParam(required = false) String status) {
        validateOrg(orgId);
        return ResponseEntity.ok(analyticsService.getOrgSummary(orgId, from, to, team_id, agent_type, status));
    }

    @GetMapping("/analytics/timeseries")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> getTimeseries(@PathVariable UUID orgId,
                                            @RequestParam String from,
                                            @RequestParam String to,
                                            @RequestParam(required = false) UUID team_id,
                                            @RequestParam(required = false) String agent_type,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String granularity) {
        validateOrg(orgId);
        return ResponseEntity.ok(analyticsService.getOrgTimeseries(orgId, from, to, team_id, agent_type, status, granularity));
    }

    @GetMapping("/analytics/by-team")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> getByTeam(@PathVariable UUID orgId,
                                        @RequestParam String from,
                                        @RequestParam String to,
                                        @RequestParam(required = false) String agent_type,
                                        @RequestParam(required = false) String status) {
        validateOrg(orgId);
        return ResponseEntity.ok(analyticsService.getByTeam(orgId, from, to, agent_type, status));
    }

    @GetMapping("/analytics/by-agent-type")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<?> getByAgentType(@PathVariable UUID orgId,
                                             @RequestParam String from,
                                             @RequestParam String to,
                                             @RequestParam(required = false) UUID team_id,
                                             @RequestParam(required = false) String status) {
        validateOrg(orgId);
        return ResponseEntity.ok(analyticsService.getByAgentType(orgId, from, to, team_id, status));
    }

    @GetMapping("/analytics/top-users")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> getTopUsers(@PathVariable UUID orgId,
                                          @RequestParam String from,
                                          @RequestParam String to,
                                          @RequestParam(required = false) UUID team_id,
                                          @RequestParam(required = false, defaultValue = "runs") String sort_by,
                                          @RequestParam(required = false, defaultValue = "10") int limit) {
        validateOrg(orgId);
        return ResponseEntity.ok(analyticsService.getTopUsers(orgId, from, to, team_id, sort_by, Math.min(limit, 50)));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> getOrgRuns(@PathVariable UUID orgId,
                                         @RequestParam String from,
                                         @RequestParam String to,
                                         @RequestParam(required = false) UUID team_id,
                                         @RequestParam(required = false) UUID user_id,
                                         @RequestParam(required = false) List<String> status,
                                         @RequestParam(required = false) String agent_type,
                                         @RequestParam(required = false, defaultValue = "0") int page,
                                         @RequestParam(required = false, defaultValue = "25") int size) {
        validateOrg(orgId);
        return ResponseEntity.ok(analyticsService.getOrgRuns(
                orgId, from, to, team_id, user_id, status, agent_type, page, Math.min(size, 100)));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> getUsers(@PathVariable UUID orgId) {
        validateOrg(orgId);
        List<User> users = userRepository.findByOrgId(orgId);
        return ResponseEntity.ok(Map.of("users",
                users.stream().map(u -> Map.of(
                        "user_id", u.getId(),
                        "display_name", u.getDisplayName(),
                        "email", u.getEmail()
                )).toList()));
    }

    @GetMapping("/teams")
    public ResponseEntity<?> getTeams(@PathVariable UUID orgId) {
        validateOrg(orgId);
        List<Team> teams = teamRepository.findByOrgId(orgId);
        return ResponseEntity.ok(Map.of("teams",
                teams.stream().map(t -> Map.of("team_id", t.getId(), "name", t.getName())).toList()));
    }

    @GetMapping("/agent-types")
    public ResponseEntity<?> getAgentTypes(@PathVariable UUID orgId) {
        validateOrg(orgId);
        List<AgentType> types = agentTypeRepository.findByOrgId(orgId);
        return ResponseEntity.ok(Map.of("agent_types",
                types.stream().map(t -> Map.of("slug", t.getSlug(), "display_name", t.getDisplayName())).toList()));
    }

    @GetMapping("/budgets")
    @PreAuthorize("hasRole('ORG_ADMIN')")
    public ResponseEntity<?> getBudgets(@PathVariable UUID orgId) {
        validateOrg(orgId);
        return ResponseEntity.ok(Map.of("budgets", budgetRepository.findByOrgId(orgId)));
    }

    private void validateOrg(UUID orgId) {
        if (!authContext.getOrgId().equals(orgId)) {
            throw new SecurityException("Access denied to organization");
        }
    }
}
