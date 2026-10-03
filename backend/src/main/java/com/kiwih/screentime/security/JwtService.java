package com.kiwih.screentime.security;

import com.kiwih.screentime.config.AppProperties;
import com.kiwih.screentime.domain.Role;
import com.kiwih.screentime.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Signs and reads the access token. Thirty days by default: a child who gets
 * logged out every day stops booking, and an app nobody books in is worse than
 * no app.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;
    private final Clock clock;

    public JwtService(AppProperties properties, Clock clock) {
        this.key = toKey(properties.getJwt().getSecret());
        this.ttl = Duration.ofDays(properties.getJwt().getTtlDays());
        this.clock = clock;
    }

    public Issued issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("displayName", user.getDisplayName())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new Issued(token, expiresAt);
    }

    /** Empty when the token is missing, malformed, expired or signed with another key. */
    public Optional<AppPrincipal> read(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new AppPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("displayName", String.class),
                    Role.valueOf(claims.get("role", String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record Issued(String token, Instant expiresAt) {
    }

    private static SecretKey toKey(String secret) {
        // a base64 secret, as openssl rand -base64 produces, is used as the
        // bytes it encodes; anything else is used as its own characters
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (RuntimeException ignored) {
            // not base64, fall through
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
