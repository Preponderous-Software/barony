package com.barony.backend.controller;

import com.barony.backend.model.Session;
import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.GuestCookies;
import com.barony.backend.service.GuestRateLimiter;
import com.barony.backend.service.GuestSessionService;
import com.barony.backend.service.SessionService;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Guest play: a game without a UserAuth account.
 *
 * {@code POST /api/guest} resumes the guest named by this browser's guest cookie, or creates a new
 * guest (rate limited) and sets the cookie. A guest has no username, cannot log in, and cannot be
 * presented as an account: the guest cookie is a separate cookie that only the guest path reads.
 *
 * {@code POST /api/guest/claim} is the "keep your progress" upgrade: a signed-in account that has no
 * game yet takes over this browser's guest game. It is insert-only on the account side and refuses
 * (409) when the account already has a game, so an existing account's game is never replaced.
 */
@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestController {

    private final GuestSessionService guestSessionService;
    private final GuestCookies guestCookies;
    private final GuestRateLimiter rateLimiter;
    private final SessionService sessionService;
    private final UserAuthClient userAuthClient;
    private final AuthCookies authCookies;

    @PostMapping
    public Map<String, Object> startOrResume(HttpServletRequest request, HttpServletResponse response) {
        Optional<Session> existing = guestCookies.read(request).flatMap(guestSessionService::find);
        if (existing.isPresent()) {
            return Map.of("guest", true, "resumed", true);
        }
        if (!rateLimiter.tryAcquire(clientAddress(request))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Too many new guest games from here. Please wait a while, or create a free account.");
        }
        GuestSessionService.NewGuest created;
        try {
            created = guestSessionService.create();
        } catch (GuestSessionService.GuestCapacityException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, guestCookies.issue(created.token()).toString());
        return Map.of("guest", true, "resumed", false);
    }

    @PostMapping("/claim")
    public Map<String, Object> claim(HttpServletRequest request, HttpServletResponse response) {
        String token = authCookies.read(request).orElse(null);
        if (token == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Log in to keep your guest progress.");
        }
        String username = userAuthClient.validate(token).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid, expired, or revoked token."));
        Session guest = guestCookies.read(request).flatMap(guestSessionService::find).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no guest game in this browser."));

        boolean claimed = sessionService.claimGuestGame(username, guest.getGameState(),
                guestSessionService.getRuns(guest));
        if (!claimed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This account already has a game, so it was kept and the guest game was not moved.");
        }
        guestSessionService.delete(guest);
        response.addHeader(HttpHeaders.SET_COOKIE, guestCookies.clear().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("claimed", true);
        body.put("username", username);
        return body;
    }

    /**
     * The caller's address for rate limiting. Behind the gateway, Traefik appends the connecting
     * client's address as the last X-Forwarded-For entry, so the last entry is the one a client
     * cannot forge (earlier entries can be). Direct connections fall back to the socket address.
     */
    static String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            String last = parts[parts.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        return request.getRemoteAddr();
    }
}
