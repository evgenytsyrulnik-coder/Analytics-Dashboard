package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.JwtUtil;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.entity.User;
import com.analytics.dashboard.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.analytics.dashboard.dto.LoginRequest;
import com.analytics.dashboard.dto.LoginResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private User testUser;
    private UUID userId;
    private UUID orgId;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        testUser = new User(userId, orgId, "ext-1", "admin@test.com", "Admin User", "$2a$10$hash", "ORG_ADMIN");
        Team team = new Team(teamId, orgId, "ext-t1", "Engineering");
        testUser.setTeams(Set.of(team));
    }

    @Test
    void loginSuccessReturnsTokenAndUserInfo() {
        LoginRequest request = new LoginRequest("admin@test.com", "password123");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$10$hash")).thenReturn(true);
        when(jwtUtil.generateToken(eq(userId), eq(orgId), eq("ORG_ADMIN"), anyList())).thenReturn("jwt-token");

        ResponseEntity<?> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        LoginResponse body = (LoginResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.token()).isEqualTo("jwt-token");
        assertThat(body.userId()).isEqualTo(userId);
        assertThat(body.orgId()).isEqualTo(orgId);
        assertThat(body.email()).isEqualTo("admin@test.com");
        assertThat(body.displayName()).isEqualTo("Admin User");
        assertThat(body.role()).isEqualTo("ORG_ADMIN");
        assertThat(body.teams()).hasSize(1);
    }

    @Test
    void loginWithWrongPasswordReturns401() {
        LoginRequest request = new LoginRequest("admin@test.com", "wrongpassword");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hash")).thenReturn(false);

        ResponseEntity<?> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginWithNonExistentEmailReturns401() {
        LoginRequest request = new LoginRequest("notfound@test.com", "password123");
        when(userRepository.findByEmail("notfound@test.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loginFailureResponseContainsErrorDetails() {
        LoginRequest request = new LoginRequest("bad@test.com", "bad");
        when(userRepository.findByEmail("bad@test.com")).thenReturn(Optional.empty());

        ResponseEntity<?> response = authController.login(request);

        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", 401);
        assertThat(body).containsEntry("title", "Unauthorized");
        assertThat(body).containsEntry("detail", "Invalid email or password");
    }

    @Test
    void loginReturnsTeamInfoInResponse() {
        LoginRequest request = new LoginRequest("admin@test.com", "password123");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$10$hash")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any(), anyList())).thenReturn("jwt-token");

        ResponseEntity<?> response = authController.login(request);

        LoginResponse body = (LoginResponse) response.getBody();
        assertThat(body.teams()).hasSize(1);
        assertThat(body.teams().get(0).teamId()).isEqualTo(teamId);
        assertThat(body.teams().get(0).teamName()).isEqualTo("Engineering");
    }

    @Test
    void loginCallsPasswordEncoderWithCorrectArguments() {
        LoginRequest request = new LoginRequest("admin@test.com", "mypassword");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("mypassword", "$2a$10$hash")).thenReturn(false);

        authController.login(request);

        verify(passwordEncoder).matches("mypassword", "$2a$10$hash");
    }

    @Test
    void loginGeneratesTokenWithCorrectTeamIds() {
        LoginRequest request = new LoginRequest("admin@test.com", "password123");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$10$hash")).thenReturn(true);
        when(jwtUtil.generateToken(any(), any(), any(), anyList())).thenReturn("jwt-token");

        authController.login(request);

        verify(jwtUtil).generateToken(eq(userId), eq(orgId), eq("ORG_ADMIN"), argThat(teamIds ->
                teamIds.size() == 1 && teamIds.contains(teamId)));
    }
}
