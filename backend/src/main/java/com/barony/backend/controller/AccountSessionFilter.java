package com.barony.backend.controller;

import com.barony.backend.service.AuthCookies;
import com.barony.backend.service.JwtExpiry;
import com.barony.backend.service.RefreshSessions;
import com.barony.backend.service.UserAuthClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Renews an account session before the controllers see the request. When the access cookie is
 * missing or about to expire and a refresh cookie is present, the refresh token is exchanged, both
 * cookies are re-set on the response, and the controllers read the new access token as though the
 * browser had sent it.
 *
 * Only requests that need an account pass through here, and a page that says it is playing as a
 * guest is left alone. A rejected refresh token clears its cookie and the request continues to the
 * usual 401. An outage never does: the cookies stay and a request with no usable access token gets
 * a 503, so the player is not sent to the login page and the next request can renew.
 */
@Component
public class AccountSessionFilter extends OncePerRequestFilter {

    /** Renewing slightly early avoids a token expiring between this check and UserAuth's. */
    static final Duration RENEWAL_MARGIN = Duration.ofSeconds(30);

    private final AuthCookies authCookies;
    private final RefreshSessions refreshSessions;

    public AccountSessionFilter(AuthCookies authCookies, RefreshSessions refreshSessions) {
        this.authCookies = authCookies;
        this.refreshSessions = refreshSessions;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        boolean needsAccount = path.startsWith("/api/session/") || path.equals("/api/guest/claim");
        return !needsAccount || "guest".equalsIgnoreCase(request.getHeader(GameController.PLAYER_MODE_HEADER));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> refreshToken = authCookies.readRefresh(request);
        Optional<String> accessToken = authCookies.read(request);
        boolean renewalDue = accessToken.map(token -> JwtExpiry.expiresWithin(token, RENEWAL_MARGIN)).orElse(true);
        if (refreshToken.isEmpty() || !renewalDue) {
            chain.doFilter(request, response);
            return;
        }

        Optional<UserAuthClient.RefreshedTokens> renewed;
        try {
            renewed = refreshSessions.exchange(refreshToken.get());
        } catch (ResponseStatusException e) {
            if (accessToken.filter(token -> !JwtExpiry.isExpired(token)).isPresent()) {
                chain.doFilter(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "Authentication service is unavailable");
            }
            return;
        }

        if (renewed.isEmpty()) {
            response.addHeader(HttpHeaders.SET_COOKIE, authCookies.clearRefresh().toString());
            chain.doFilter(request, response);
            return;
        }
        UserAuthClient.RefreshedTokens tokens = renewed.get();
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.issue(tokens.token(), tokens.expiresAt()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, authCookies.issueRefresh(tokens.refreshToken()).toString());
        chain.doFilter(withAccessToken(request, tokens.token()), response);
    }

    private HttpServletRequest withAccessToken(HttpServletRequest request, String token) {
        List<Cookie> cookies = new ArrayList<>();
        if (request.getCookies() != null) {
            Arrays.stream(request.getCookies())
                    .filter(cookie -> !AuthCookies.COOKIE_NAME.equals(cookie.getName()))
                    .forEach(cookies::add);
        }
        cookies.add(new Cookie(AuthCookies.COOKIE_NAME, token));
        Cookie[] renewed = cookies.toArray(new Cookie[0]);
        return new HttpServletRequestWrapper(request) {
            @Override
            public Cookie[] getCookies() {
                return renewed;
            }
        };
    }
}
