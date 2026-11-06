package com.tmc.security.jwt;

import com.tmc.security.api.AuthenticatedUser;
import com.tmc.security.api.Role;
import com.tmc.shared.config.TmcProperties;
import com.tmc.shared.error.DomainException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long ttlSeconds;
    private final Clock clock;

    public JwtService(TmcProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.security().jwtSecret()));
        this.ttlSeconds = properties.security().jwtTtl().toSeconds();
        this.clock = clock;
    }

    public String issue(AuthenticatedUser user) {
        Instant now = clock.instant();
        var builder = Jwts.builder()
                .subject(user.username())
                .claim("role", user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(key);
        if (user.employeeId() != null) {
            builder.claim("employeeId", user.employeeId().toString());
        }
        return builder.compact();
    }

    public AuthenticatedUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String employeeId = claims.get("employeeId", String.class);
            return new AuthenticatedUser(
                    claims.getSubject(),
                    Role.valueOf(claims.get("role", String.class)),
                    employeeId == null ? null : UUID.fromString(employeeId));
        } catch (Exception e) {
            throw new DomainException("INVALID_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
