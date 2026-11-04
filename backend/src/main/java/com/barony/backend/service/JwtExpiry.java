package com.barony.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Reads a JWT's {@code exp} claim to decide whether it is worth renewing. The signature is not
 * checked, so this is never an authentication decision: UserAuth still validates every token.
 * A token whose expiry cannot be read counts as unexpired, which leaves that call to UserAuth.
 */
public final class JwtExpiry {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JwtExpiry() {
    }

    public static boolean isExpired(String token) {
        return expiresWithin(token, Duration.ZERO);
    }

    public static boolean expiresWithin(String token, Duration margin) {
        return expiry(token).map(at -> !at.isAfter(Instant.now().plus(margin))).orElse(false);
    }

    private static Optional<Instant> expiry(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            JsonNode exp = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1])).get("exp");
            return exp != null && exp.isNumber()
                    ? Optional.of(Instant.ofEpochSecond(exp.asLong()))
                    : Optional.empty();
        } catch (java.io.IOException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
