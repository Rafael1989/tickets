package com.ticketwave.auth;

import com.ticketwave.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Issues and validates the JWT access tokens used across the API.
 */
@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    /**
     * RFC 7518 §3.2 requires an HMAC-SHA key to be at least as long as the hash
     * output — 256 bits, so 32 bytes, for HS256.
     */
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(requireStrongSecret(properties.secret()));
    }

    /**
     * Checked here rather than left to jjwt, which rejects a short key with a
     * WeakKeyException quoting RFC 7518 at the reader — accurate, but it never
     * mentions JWT_SECRET, so the reader is told the algorithm is unhappy
     * rather than which variable to fix. Startup already fails fast when the
     * variable is missing; failing just as clearly when it is present but too
     * short is the same guarantee.
     *
     * Reports the length only. The value is a signing key and must never reach
     * a log, which is exactly where a startup failure message ends up.
     */
    private static byte[] requireStrongSecret(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MINIMUM_SECRET_BYTES + " characters (256 bits) for HMAC-SHA signing, but is "
                            + bytes.length + ". Set a longer value, for example: "
                            + "export JWT_SECRET=\"$(openssl rand -base64 48)\"");
        }
        return bytes;
    }

    public String generateAccessToken(String subject, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plus(properties.accessTokenTtlMinutes(), ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Claims claims = parseClaims(token);
        return (List<String>) claims.getOrDefault(ROLES_CLAIM, List.of());
    }

    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenTtlMinutes() * 60;
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
