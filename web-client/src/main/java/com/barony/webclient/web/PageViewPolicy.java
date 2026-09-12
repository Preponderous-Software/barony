package com.barony.webclient.web;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides which requests count as a page view and what path is reported for them. Pure functions
 * over the few request facts involved, so the rules are tested without a servlet container and
 * the interceptor that applies them stays trivial.
 *
 * <p>A page view is a successful {@code GET} that produced an HTML page for what looks like a
 * person's browser. Everything else is left out: the API the game page calls, static assets and
 * their content-hashed variants, error pages, redirects, and crawlers, monitors and scripted
 * clients -- none of which say anything about how often people open the game.
 */
public final class PageViewPolicy {

    /** Longer paths are truncated: a tag is a label for a count, not a place to keep a URL. */
    static final int MAX_PAGE_LENGTH = 200;

    /** Path prefixes that are machinery or assets rather than pages. */
    private static final List<String> SKIPPED_PREFIXES = List.of(
            "/css", "/js", "/images", "/img", "/fonts", "/webjars", "/static", "/assets",
            "/actuator", "/api", "/error", "/favicon.ico", "/robots.txt", "/sitemap.xml");

    /** A last path segment ending in an extension is a file, not a page (e.g. a versioned asset). */
    private static final Pattern FILE_EXTENSION = Pattern.compile("\\.[A-Za-z0-9]+$");

    /**
     * Substrings that mark a user agent as a crawler, uptime monitor, link previewer or scripted
     * client. Matched case-insensitively; the list is deliberately broad, since counting a bot is
     * worse than missing an unusual browser.
     */
    private static final List<String> NON_HUMAN_AGENTS = List.of(
            "bot", "crawler", "spider", "slurp", "curl", "wget", "python-requests",
            "go-http-client", "headlesschrome", "lighthouse", "uptimerobot", "facebookexternalhit",
            "discordbot", "twitterbot", "whatsapp", "telegrambot");

    private PageViewPolicy() {
    }

    /**
     * Whether a completed request should be reported as a page view.
     *
     * @param method      the HTTP method
     * @param status      the response status
     * @param contentType the response {@code Content-Type} as finally set (may be null)
     * @param path        the request path, already normalised by {@link #pagePath}
     * @param userAgent   the request {@code User-Agent} header (may be null)
     */
    public static boolean shouldReport(String method, int status, String contentType, String path,
                                       String userAgent) {
        if (!"GET".equalsIgnoreCase(method)) {
            return false;
        }
        if (status < 200 || status > 299) {
            return false;
        }
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("text/html")) {
            return false;
        }
        if (path == null || path.isEmpty() || isSkippedPath(path)) {
            return false;
        }
        return isHuman(userAgent);
    }

    /**
     * The path reported for a request: the path alone, with any query string or fragment removed,
     * without a trailing slash (the root stays {@code /}), and cut to {@value #MAX_PAGE_LENGTH}
     * characters.
     */
    public static String pagePath(String requestUri) {
        if (requestUri == null || requestUri.isEmpty()) {
            return "/";
        }
        String path = requestUri;
        int cut = indexOfEither(path, '?', '#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.isEmpty()) {
            path = "/";
        }
        if (path.length() > MAX_PAGE_LENGTH) {
            path = path.substring(0, MAX_PAGE_LENGTH);
        }
        return path;
    }

    private static boolean isSkippedPath(String path) {
        for (String prefix : SKIPPED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        String lastSegment = path.substring(path.lastIndexOf('/') + 1);
        return FILE_EXTENSION.matcher(lastSegment).find();
    }

    private static boolean isHuman(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return false;
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        for (String marker : NON_HUMAN_AGENTS) {
            if (agent.contains(marker)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOfEither(String s, char a, char b) {
        int ia = s.indexOf(a);
        int ib = s.indexOf(b);
        if (ia < 0) {
            return ib;
        }
        if (ib < 0) {
            return ia;
        }
        return Math.min(ia, ib);
    }
}
