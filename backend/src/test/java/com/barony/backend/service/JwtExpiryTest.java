package com.barony.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtExpiryTest {

    private static String jwtWithClaims(String claims) {
        return "h." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8)) + ".s";
    }

    private static String jwtExpiringIn(long seconds) {
        return jwtWithClaims("{\"exp\":" + Instant.now().plusSeconds(seconds).getEpochSecond() + "}");
    }

    @Test
    void aTokenPastItsExpiryIsExpired() {
        assertTrue(JwtExpiry.isExpired(jwtExpiringIn(-60)));
    }

    @Test
    void aTokenBeforeItsExpiryIsNotExpired() {
        assertFalse(JwtExpiry.isExpired(jwtExpiringIn(60)));
    }

    @Test
    void aTokenExpiringThisSecondIsExpired() {
        assertTrue(JwtExpiry.isExpired(jwtExpiringIn(0)));
    }

    @Test
    void aFractionalExpiryIsRead() {
        long past = Instant.now().minusSeconds(60).getEpochSecond();

        assertTrue(JwtExpiry.isExpired(jwtWithClaims("{\"exp\":" + past + ".5}")));
    }

    @Test
    void anExpiryInsideTheMarginCounts() {
        assertTrue(JwtExpiry.expiresWithin(jwtExpiringIn(10), Duration.ofSeconds(30)));
    }

    @Test
    void anExpiryBeyondTheMarginDoesNotCount() {
        assertFalse(JwtExpiry.expiresWithin(jwtExpiringIn(600), Duration.ofSeconds(30)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "opaque-token", "h.p", "h.p.s.extra", "h.not*base64.s"})
    void aTokenThatIsNotAJwtCountsAsUnexpired(String token) {
        assertUnreadable(token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "[1,2]", "null", "{}", "{\"sub\":\"alice\"}",
            "{\"exp\":null}", "{\"exp\":\"1\"}", "{\"exp\":true}"})
    void claimsWithoutANumericExpiryCountAsUnexpired(String claims) {
        assertUnreadable(jwtWithClaims(claims));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e30", "-1e30", "31556889864403200", "-31557014167219201",
            "9223372036854775807", "-9223372036854775808"})
    void anExpiryOutsideTheRangeOfAnInstantCountsAsUnexpired(String exp) {
        assertUnreadable(jwtWithClaims("{\"exp\":" + exp + "}"));
    }

    private static void assertUnreadable(String token) {
        assertFalse(JwtExpiry.isExpired(token));
        assertFalse(JwtExpiry.expiresWithin(token, Duration.ofDays(365)));
    }
}
