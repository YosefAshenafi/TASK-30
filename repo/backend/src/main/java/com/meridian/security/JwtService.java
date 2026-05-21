package com.meridian.security;

import com.meridian.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(UserDetails user, UUID userId, String role) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getUsername())
                .claims(Map.of(
                        "userId", userId.toString(),
                        "role", role,
                        "type", "ACCESS"
                ))
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.accessExpiryMs()))
                .signWith(getAccessKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of("type", "REFRESH"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.refreshExpiryMs()))
                .signWith(getRefreshKey())
                .compact();
    }

    public boolean validateToken(String token, SecretKey key) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException ex) {
            log.warn("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    public String extractUsername(String token, SecretKey key) {
        return extractClaim(token, key, Claims::getSubject);
    }

    public <T> T extractClaim(String token, SecretKey key, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    public SecretKey getAccessKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.accessSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    public SecretKey getRefreshKey() {
        return Keys.hmacShaKeyFor(
                jwtProperties.refreshSecret().getBytes(StandardCharsets.UTF_8)
        );
    }
}
