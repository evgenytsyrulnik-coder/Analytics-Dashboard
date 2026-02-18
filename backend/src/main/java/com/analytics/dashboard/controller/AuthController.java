package com.analytics.dashboard.controller;

import com.analytics.dashboard.config.JwtUtil;
import com.analytics.dashboard.dto.LoginRequest;
import com.analytics.dashboard.dto.LoginResponse;
import com.analytics.dashboard.entity.Team;
import com.analytics.dashboard.repository.OrganizationRepository;
import com.analytics.dashboard.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, OrganizationRepository organizationRepository,
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        return userRepository.findByEmail(request.email())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
                .<ResponseEntity<?>>map(user -> {
                    List<UUID> teamIds = user.getTeams().stream().map(Team::getId).toList();
                    String token = jwtUtil.generateToken(user.getId(), user.getOrgId(), user.getRole(), teamIds);

                    List<LoginResponse.TeamInfo> teamInfos = user.getTeams().stream()
                            .map(t -> new LoginResponse.TeamInfo(t.getId(), t.getName()))
                            .toList();

                    String orgName = organizationRepository.findById(user.getOrgId())
                            .map(org -> org.getName())
                            .orElse("Unknown");

                    return ResponseEntity.ok(new LoginResponse(
                            token, user.getId(), user.getOrgId(), orgName,
                            user.getEmail(), user.getDisplayName(),
                            user.getRole(), teamInfos
                    ));
                })
                .orElse(ResponseEntity.status(401).body(
                        Map.of("type", "https://analytics.example.com/errors/unauthorized",
                               "title", "Unauthorized",
                               "status", 401,
                               "detail", "Invalid email or password")
                ));
    }
}
