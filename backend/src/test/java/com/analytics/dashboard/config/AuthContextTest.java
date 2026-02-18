package com.analytics.dashboard.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthContextTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private Claims claims;

    private AuthContext authContext;

    @BeforeEach
    void setUp() {
        authContext = new AuthContext(jwtUtil);
        var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getUserIdDelegatesToJwtUtil() {
        UUID userId = UUID.randomUUID();
        when(jwtUtil.getUserId(claims)).thenReturn(userId);

        UUID result = authContext.getUserId();

        assertThat(result).isEqualTo(userId);
        verify(jwtUtil).getUserId(claims);
    }

    @Test
    void getOrgIdDelegatesToJwtUtil() {
        UUID orgId = UUID.randomUUID();
        when(jwtUtil.getOrgId(claims)).thenReturn(orgId);

        UUID result = authContext.getOrgId();

        assertThat(result).isEqualTo(orgId);
        verify(jwtUtil).getOrgId(claims);
    }

    @Test
    void getRolesDelegatesToJwtUtil() {
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("ORG_ADMIN"));

        List<String> result = authContext.getRoles();

        assertThat(result).containsExactly("ORG_ADMIN");
    }

    @Test
    void getTeamIdsParsesStringTeamIds() {
        UUID teamId = UUID.randomUUID();
        when(jwtUtil.getTeams(claims)).thenReturn(List.of(teamId.toString()));

        List<UUID> result = authContext.getTeamIds();

        assertThat(result).containsExactly(teamId);
    }

    @Test
    void isOrgAdminReturnsTrueForOrgAdmin() {
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("ORG_ADMIN"));

        assertThat(authContext.isOrgAdmin()).isTrue();
    }

    @Test
    void isOrgAdminReturnsFalseForNonAdmin() {
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("MEMBER"));

        assertThat(authContext.isOrgAdmin()).isFalse();
    }

    @Test
    void isTeamLeadReturnsTrueForTeamLead() {
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("TEAM_LEAD"));

        assertThat(authContext.isTeamLead()).isTrue();
    }

    @Test
    void isTeamLeadReturnsFalseForMember() {
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("MEMBER"));

        assertThat(authContext.isTeamLead()).isFalse();
    }

    @Test
    void hasTeamAccessReturnsTrueForOrgAdmin() {
        UUID teamId = UUID.randomUUID();
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("ORG_ADMIN"));

        assertThat(authContext.hasTeamAccess(teamId)).isTrue();
    }

    @Test
    void hasTeamAccessReturnsTrueWhenUserBelongsToTeam() {
        UUID teamId = UUID.randomUUID();
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("MEMBER"));
        when(jwtUtil.getTeams(claims)).thenReturn(List.of(teamId.toString()));

        assertThat(authContext.hasTeamAccess(teamId)).isTrue();
    }

    @Test
    void hasTeamAccessReturnsFalseWhenUserDoesNotBelongToTeam() {
        UUID teamId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("MEMBER"));
        when(jwtUtil.getTeams(claims)).thenReturn(List.of(otherTeamId.toString()));

        assertThat(authContext.hasTeamAccess(teamId)).isFalse();
    }

    @Test
    void hasTeamAccessReturnsFalseForMemberWithNoTeams() {
        UUID teamId = UUID.randomUUID();
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("MEMBER"));
        when(jwtUtil.getTeams(claims)).thenReturn(List.of());

        assertThat(authContext.hasTeamAccess(teamId)).isFalse();
    }
}
