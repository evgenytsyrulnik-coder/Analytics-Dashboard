package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.AuthContext;
import com.analytics.dashboard.service.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams/{teamId}")
public class TeamAnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthContext authContext;

    public TeamAnalyticsController(AnalyticsService analyticsService, AuthContext authContext) {
        this.analyticsService = analyticsService;
        this.authContext = authContext;
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<?> getSummary(@PathVariable UUID teamId,
                                         @RequestParam String from,
                                         @RequestParam String to,
                                         @RequestParam(required = false) String agent_type,
                                         @RequestParam(required = false) String status) {
        validateTeamAccess(teamId);
        return ResponseEntity.ok(analyticsService.getTeamSummary(teamId, from, to, agent_type, status));
    }

    @GetMapping("/analytics/timeseries")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<?> getTimeseries(@PathVariable UUID teamId,
                                            @RequestParam String from,
                                            @RequestParam String to,
                                            @RequestParam(required = false) String agent_type,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) String granularity) {
        validateTeamAccess(teamId);
        return ResponseEntity.ok(analyticsService.getTeamTimeseries(teamId, from, to, agent_type, status, granularity));
    }

    @GetMapping("/analytics/by-user")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'TEAM_LEAD')")
    public ResponseEntity<?> getByUser(@PathVariable UUID teamId,
                                        @RequestParam String from,
                                        @RequestParam String to,
                                        @RequestParam(required = false) String agent_type,
                                        @RequestParam(required = false) String status) {
        validateTeamAccess(teamId);
        return ResponseEntity.ok(analyticsService.getTeamByUser(teamId, from, to, agent_type, status));
    }

    private void validateTeamAccess(UUID teamId) {
        if (!authContext.hasTeamAccess(teamId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to team");
        }
    }
}
