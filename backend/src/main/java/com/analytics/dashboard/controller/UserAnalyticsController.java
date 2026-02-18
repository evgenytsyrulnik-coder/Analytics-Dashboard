package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.AuthContext;
import com.analytics.dashboard.service.UserAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class UserAnalyticsController {

    private final UserAnalyticsService analyticsService;
    private final AuthContext authContext;
    private final com.analytics.dashboard.repository.UserRepository userRepository;

    public UserAnalyticsController(UserAnalyticsService analyticsService, AuthContext authContext,
                                   com.analytics.dashboard.repository.UserRepository userRepository) {
        this.analyticsService = analyticsService;
        this.authContext = authContext;
        this.userRepository = userRepository;
    }

    @GetMapping("/users/me/analytics/summary")
    public ResponseEntity<?> getMySummary(@RequestParam String from,
                                           @RequestParam String to,
                                           @RequestParam(required = false) String agent_type,
                                           @RequestParam(required = false) String status) {
        return ResponseEntity.ok(analyticsService.getUserSummary(
                authContext.getUserId(), authContext.getOrgId(), from, to, agent_type, status));
    }

    @GetMapping("/users/me/analytics/timeseries")
    public ResponseEntity<?> getMyTimeseries(@RequestParam String from,
                                              @RequestParam String to,
                                              @RequestParam(required = false) String agent_type,
                                              @RequestParam(required = false) String status) {
        return ResponseEntity.ok(analyticsService.getUserTimeseries(
                authContext.getUserId(), from, to, agent_type, status));
    }

    @GetMapping("/users/me/runs")
    public ResponseEntity<?> getMyRuns(@RequestParam String from,
                                        @RequestParam String to,
                                        @RequestParam(required = false) String agent_type,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false, defaultValue = "50") int limit) {
        return ResponseEntity.ok(analyticsService.getUserRuns(
                authContext.getUserId(), from, to, agent_type, status, Math.min(limit, 200)));
    }

    @GetMapping("/users/{userId}/analytics/summary")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<?> getUserSummary(@PathVariable UUID userId,
                                             @RequestParam String from,
                                             @RequestParam String to,
                                             @RequestParam(required = false) String agent_type,
                                             @RequestParam(required = false) String status) {
        validateUserAccess(userId);
        return ResponseEntity.ok(analyticsService.getUserSummary(
                userId, authContext.getOrgId(), from, to, agent_type, status));
    }

    @GetMapping("/users/{userId}/analytics/timeseries")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<?> getUserTimeseries(@PathVariable UUID userId,
                                                @RequestParam String from,
                                                @RequestParam String to,
                                                @RequestParam(required = false) String agent_type,
                                                @RequestParam(required = false) String status) {
        validateUserAccess(userId);
        return ResponseEntity.ok(analyticsService.getUserTimeseries(
                userId, from, to, agent_type, status));
    }

    @GetMapping("/users/{userId}/runs")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<?> getUserRuns(@PathVariable UUID userId,
                                          @RequestParam String from,
                                          @RequestParam String to,
                                          @RequestParam(required = false) String agent_type,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(required = false, defaultValue = "50") int limit) {
        validateUserAccess(userId);
        return ResponseEntity.ok(analyticsService.getUserRuns(
                userId, from, to, agent_type, status, Math.min(limit, 200)));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<?> getRunDetail(@PathVariable UUID runId) {
        var detail = analyticsService.getRunDetail(runId);
        // Verify access: must be owner, team lead, or org admin
        UUID userId = authContext.getUserId();
        if (!detail.userId().equals(userId) && !authContext.isOrgAdmin()
                && !(authContext.isTeamLead() && authContext.hasTeamAccess(detail.teamId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to run");
        }
        return ResponseEntity.ok(detail);
    }

    private void validateUserAccess(UUID userId) {
        var targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!targetUser.getOrgId().equals(authContext.getOrgId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to user");
        }
        if (authContext.isOrgAdmin()) {
            return;
        }
        // TEAM_LEAD: can only view users who share at least one team
        boolean sharesTeam = targetUser.getTeams().stream()
                .anyMatch(team -> authContext.getTeamIds().contains(team.getId()));
        if (!sharesTeam) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to user");
        }
    }
}
