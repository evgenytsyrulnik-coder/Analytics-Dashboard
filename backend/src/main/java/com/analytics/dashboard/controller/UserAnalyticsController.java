package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.AuthContext;
import com.analytics.dashboard.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class UserAnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthContext authContext;

    public UserAnalyticsController(AnalyticsService analyticsService, AuthContext authContext) {
        this.analyticsService = analyticsService;
        this.authContext = authContext;
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
}
