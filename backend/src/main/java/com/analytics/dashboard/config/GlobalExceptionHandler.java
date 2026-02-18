package com.analytics.dashboard.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "type", "https://analytics.example.com/errors/not-found",
                "title", "Not Found",
                "status", 404,
                "detail", e.getMessage()
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "type", "https://analytics.example.com/errors/forbidden",
                "title", "Forbidden",
                "status", 403,
                "detail", "Insufficient permissions"
        ));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "type", "https://analytics.example.com/errors/forbidden",
                "title", "Forbidden",
                "status", 403,
                "detail", e.getMessage()
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of(
                "type", "https://analytics.example.com/errors/" + e.getStatusCode(),
                "title", e.getReason() != null ? e.getReason() : "Error",
                "status", e.getStatusCode().value(),
                "detail", e.getReason() != null ? e.getReason() : "An error occurred"
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "type", "https://analytics.example.com/errors/bad-request",
                "title", "Bad Request",
                "status", 400,
                "detail", e.getMessage()
        ));
    }
}
