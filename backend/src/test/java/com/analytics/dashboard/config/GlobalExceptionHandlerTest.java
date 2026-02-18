package com.analytics.dashboard.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFoundReturns404() {
        NoSuchElementException ex = new NoSuchElementException("Resource not found");

        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("status", 404);
        assertThat(response.getBody()).containsEntry("title", "Not Found");
        assertThat(response.getBody()).containsEntry("detail", "Resource not found");
        assertThat(response.getBody()).containsKey("type");
    }

    @Test
    void handleForbiddenReturns403() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<Map<String, Object>> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403);
        assertThat(response.getBody()).containsEntry("title", "Forbidden");
        assertThat(response.getBody()).containsEntry("detail", "Insufficient permissions");
    }

    @Test
    void handleSecurityExceptionReturns403() {
        SecurityException ex = new SecurityException("Access denied to organization");

        ResponseEntity<Map<String, Object>> response = handler.handleSecurityException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("status", 403);
        assertThat(response.getBody()).containsEntry("detail", "Access denied to organization");
    }

    @Test
    void handleResponseStatusReturnsCorrectStatus() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to team");

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("title", "Access denied to team");
        assertThat(response.getBody()).containsEntry("detail", "Access denied to team");
    }

    @Test
    void handleResponseStatusWithNullReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);

        ResponseEntity<Map<String, Object>> response = handler.handleResponseStatus(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("title", "Error");
        assertThat(response.getBody()).containsEntry("detail", "An error occurred");
    }

    @Test
    void handleBadRequestReturns400() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid parameter");

        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("status", 400);
        assertThat(response.getBody()).containsEntry("title", "Bad Request");
        assertThat(response.getBody()).containsEntry("detail", "Invalid parameter");
    }

    @Test
    void allResponsesContainTypeField() {
        ResponseEntity<Map<String, Object>> notFound = handler.handleNotFound(new NoSuchElementException(""));
        ResponseEntity<Map<String, Object>> forbidden = handler.handleForbidden(new AccessDeniedException(""));
        ResponseEntity<Map<String, Object>> security = handler.handleSecurityException(new SecurityException(""));
        ResponseEntity<Map<String, Object>> badRequest = handler.handleBadRequest(new IllegalArgumentException(""));

        assertThat(notFound.getBody().get("type").toString()).contains("errors/not-found");
        assertThat(forbidden.getBody().get("type").toString()).contains("errors/forbidden");
        assertThat(security.getBody().get("type").toString()).contains("errors/forbidden");
        assertThat(badRequest.getBody().get("type").toString()).contains("errors/bad-request");
    }
}
