package com.analytics.dashboard.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setsAuthenticationForValidToken() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        Claims claims = mock(Claims.class);

        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("ORG_ADMIN"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(claims);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORG_ADMIN"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void setsMultipleAuthorities() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        Claims claims = mock(Claims.class);

        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("ORG_ADMIN", "TEAM_LEAD"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .hasSize(2);
    }

    @Test
    void continuesFilterChainWithoutAuthForNoHeader() throws ServletException, IOException {
        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void continuesFilterChainWithoutAuthForNonBearerHeader() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic sometoken");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void continuesFilterChainWithoutAuthForInvalidToken() throws ServletException, IOException {
        String token = "invalid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        when(jwtUtil.parseToken(token)).thenThrow(new RuntimeException("Invalid token"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void extractsTokenAfterBearerPrefix() throws ServletException, IOException {
        String token = "my.actual.token";
        request.addHeader("Authorization", "Bearer " + token);
        Claims claims = mock(Claims.class);

        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("MEMBER"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(jwtUtil).parseToken("my.actual.token");
    }

    @Test
    void prefixesRolesWithROLE() throws ServletException, IOException {
        String token = "valid.jwt.token";
        request.addHeader("Authorization", "Bearer " + token);
        Claims claims = mock(Claims.class);

        when(jwtUtil.parseToken(token)).thenReturn(claims);
        when(jwtUtil.getRoles(claims)).thenReturn(List.of("MEMBER"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER"));
    }
}
