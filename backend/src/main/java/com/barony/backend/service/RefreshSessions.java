package com.barony.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Exchanges refresh tokens so that concurrent requests from one browser spend a token only once.
 *
 * UserAuth's refresh tokens are single-use, and presenting a spent one ends the whole session as
 * suspected theft. The game page sends overlapping requests, so several can arrive holding the same
 * token just as the access token expires. One of them performs the exchange, the others wait for
 * it, and for {@link #GRACE} afterwards a request still holding the old token receives the same
 * successor. Rejections and outages are not remembered, so the next request tries again.
 *
 * The record is in memory and keyed by a digest of the token. A single backend instance is
 * assumed; after a restart a straggler holding a spent token is treated as a replay.
 */
@Component
public class RefreshSessions {

    static final Duration GRACE = Duration.ofSeconds(60);

    /** Longer than UserAuthClient's connect and read timeouts together. */
    private static final long WAIT_SECONDS = 20;

    private static final class Exchange {
        private final CompletableFuture<Optional<UserAuthClient.RefreshedTokens>> result = new CompletableFuture<>();
        private volatile Instant finishedAt;
    }

    private final UserAuthClient userAuthClient;
    private final Clock clock;
    private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();

    @Autowired
    public RefreshSessions(UserAuthClient userAuthClient) {
        this(userAuthClient, Clock.systemUTC());
    }

    RefreshSessions(UserAuthClient userAuthClient, Clock clock) {
        this.userAuthClient = userAuthClient;
        this.clock = clock;
    }

    /**
     * Exchange a refresh token. Returns empty when UserAuth rejects it, and throws 503 when
     * UserAuth cannot answer, which callers must not treat as a sign-out.
     */
    public Optional<UserAuthClient.RefreshedTokens> exchange(String refreshToken) {
        prune();
        String key = digest(refreshToken);
        Exchange mine = new Exchange();
        Exchange existing = exchanges.putIfAbsent(key, mine);
        return existing != null ? await(existing) : perform(key, mine, refreshToken);
    }

    private Optional<UserAuthClient.RefreshedTokens> perform(String key, Exchange exchange, String refreshToken) {
        try {
            Optional<UserAuthClient.RefreshedTokens> tokens = userAuthClient.refresh(refreshToken);
            if (tokens.isPresent()) {
                exchange.finishedAt = clock.instant();
            } else {
                exchanges.remove(key);
            }
            exchange.result.complete(tokens);
            return tokens;
        } catch (RuntimeException e) {
            exchanges.remove(key);
            exchange.result.completeExceptionally(e);
            throw e;
        }
    }

    private Optional<UserAuthClient.RefreshedTokens> await(Exchange exchange) {
        try {
            return exchange.result.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ResponseStatusException status) {
                throw status;
            }
            throw unavailable(e);
        } catch (TimeoutException e) {
            throw unavailable(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        }
    }

    private void prune() {
        Instant oldest = clock.instant().minus(GRACE);
        exchanges.values().removeIf(exchange ->
                exchange.finishedAt != null && exchange.finishedAt.isBefore(oldest));
    }

    private ResponseStatusException unavailable(Exception cause) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Authentication service is unavailable", cause);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
