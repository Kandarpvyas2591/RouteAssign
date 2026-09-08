package com.routeassign.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT token generation, validation, and claim extraction.
 * Uses jjwt 0.11.5 API.
 *
 * Properties read from application.properties:
 *   app.jwt.secret        — signing secret (plain string or Base64)
 *   app.jwt.expiration-ms — token lifetime in milliseconds (default 86400000 = 24 h)
 */
@Slf4j
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    // ── Token generation ──────────────────────────────────────────────────────

    /** Generates a signed JWT with extra claims embedded in the payload. */
    public String generateToken(String subject, Map<String, Object> extraClaims) {
        return Jwts.builder()
                .setClaims(extraClaims)           // 0.11.x: setClaims / setSubject
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** Generates a token with no extra claims. */
    public String generateToken(String subject) {
        return generateToken(subject, new HashMap<>());
    }

    /** Generates a token embedding userId and role as custom claims. */
    public String generateToken(String email, Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role",   role);
        return generateToken(email, claims);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the token is parseable, not expired, and the
     * subject matches the expected value.
     */
    public boolean isTokenValid(String token, String expectedSubject) {
        try {
            String subject = extractSubject(token);
            return subject.equals(expectedSubject) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ── Claim extraction ──────────────────────────────────────────────────────

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object userId = extractAllClaims(token).get("userId");
        if (userId instanceof Integer) return ((Integer) userId).longValue();
        if (userId instanceof Long)    return (Long) userId;
        return null;
    }

    public String extractRole(String token) {
        return (String) extractAllClaims(token).get("role");
    }

    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()           // 0.11.x: parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private SecretKey getSigningKey() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            // Fall back to raw UTF-8 bytes if the secret is not valid Base64
            return Keys.hmacShaKeyFor(jwtSecret.getBytes());
        }
    }
}
