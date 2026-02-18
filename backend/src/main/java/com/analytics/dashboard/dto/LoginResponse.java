package com.analytics.dashboard.dto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
    String token,
    UUID userId,
    UUID orgId,
    String email,
    String displayName,
    String role,
    List<TeamInfo> teams
) {
    public record TeamInfo(UUID teamId, String teamName) {}
}
