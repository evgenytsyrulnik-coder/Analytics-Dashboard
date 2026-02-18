package com.analytics.dashboard.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;
    private final IdentityProviderConfig.ClaimsMapping claimsMapping;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs,
                   IdentityProviderConfig idpConfig) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.claimsMapping = idpConfig.getOidc().getClaims();
    }

    public String generateToken(UUID userId, UUID orgId, String role, List<UUID> teamIds) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("org_id", orgId.toString())
                .claim("roles", List.of(role))
                .claim("teams", teamIds.stream().map(UUID::toString).toList())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(Claims claims) {
        String claim = claimsMapping.getUserId();
        if ("sub".equals(claim)) {
            return UUID.fromString(claims.getSubject());
        }
        return UUID.fromString(claims.get(claim, String.class));
    }

    public UUID getOrgId(Claims claims) {
        return UUID.fromString(claims.get(claimsMapping.getOrgId(), String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        return claims.get(claimsMapping.getRoles(), List.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getTeams(Claims claims) {
        return claims.get(claimsMapping.getTeams(), List.class);
    }
}
