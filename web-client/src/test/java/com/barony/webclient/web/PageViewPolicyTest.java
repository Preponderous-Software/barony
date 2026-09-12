package com.barony.webclient.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down which requests count as a page view and how the reported path is shaped. Every skip
 * rule has a case of its own, so a rule that is loosened by accident shows up here rather than as
 * inflated counts in trace.
 */
class PageViewPolicyTest {

    private static final String BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/128.0.0.0 Safari/537.36";
    private static final String HTML = "text/html;charset=UTF-8";

    @Test
    void successfulHtmlGetFromBrowserIsReported() {
        assertTrue(PageViewPolicy.shouldReport("GET", 200, HTML, "/login", BROWSER));
        assertTrue(PageViewPolicy.shouldReport("get", 200, "TEXT/HTML", "/game", BROWSER));
    }

    @Test
    void nonGetIsSkipped() {
        assertFalse(PageViewPolicy.shouldReport("POST", 200, HTML, "/login", BROWSER));
        assertFalse(PageViewPolicy.shouldReport("HEAD", 200, HTML, "/login", BROWSER));
    }

    @Test
    void non2xxIsSkipped() {
        assertFalse(PageViewPolicy.shouldReport("GET", 302, HTML, "/", BROWSER));
        assertFalse(PageViewPolicy.shouldReport("GET", 404, HTML, "/missing", BROWSER));
        assertFalse(PageViewPolicy.shouldReport("GET", 500, HTML, "/game", BROWSER));
        assertFalse(PageViewPolicy.shouldReport("GET", 199, HTML, "/game", BROWSER));
    }

    @Test
    void nonHtmlResponseIsSkipped() {
        assertFalse(PageViewPolicy.shouldReport("GET", 200, "application/json", "/state", BROWSER));
        assertFalse(PageViewPolicy.shouldReport("GET", 200, "text/plain", "/state", BROWSER));
        assertFalse(PageViewPolicy.shouldReport("GET", 200, null, "/state", BROWSER));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/css/style.css", "/css/style-0a1b2c3d4e5f.css", "/js/game-logic.js", "/images/logo",
            "/img/logo", "/fonts/serif", "/webjars/x", "/static/x", "/assets/x", "/actuator/health",
            "/api/state", "/api/session/state", "/error", "/favicon.ico", "/robots.txt", "/sitemap.xml"})
    void staticResourcesAndMachineryAreSkipped(String path) {
        assertFalse(PageViewPolicy.shouldReport("GET", 200, HTML, path, BROWSER), path);
    }

    @Test
    void pathWhoseLastSegmentHasAnExtensionIsSkipped() {
        assertFalse(PageViewPolicy.shouldReport("GET", 200, HTML, "/game.html", BROWSER));
        assertFalse(PageViewPolicy.shouldReport("GET", 200, HTML, "/guide/rules.pdf", BROWSER));
        // A dot in an earlier segment is not an extension.
        assertTrue(PageViewPolicy.shouldReport("GET", 200, HTML, "/v1.2/game", BROWSER));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)",
            "Mozilla/5.0 (compatible; SomeCrawler/1.0)",
            "Mozilla/5.0 (compatible; Baiduspider/2.0)",
            "Mozilla/5.0 (compatible; Yahoo! Slurp)",
            "curl/8.5.0",
            "Wget/1.21",
            "python-requests/2.31",
            "Go-http-client/1.1",
            "Mozilla/5.0 HeadlessChrome/120.0",
            "Mozilla/5.0 Chrome-Lighthouse",
            "UptimeRobot/2.0",
            "facebookexternalhit/1.1",
            "Mozilla/5.0 (compatible; Discordbot/2.0)",
            "Twitterbot/1.0",
            "WhatsApp/2.23",
            "TelegramBot (like TwitterBot)",
            "GOOGLEBOT"})
    void crawlersMonitorsAndScriptedClientsAreSkipped(String userAgent) {
        assertFalse(PageViewPolicy.shouldReport("GET", 200, HTML, "/login", userAgent), userAgent);
    }

    @Test
    void missingUserAgentIsSkipped() {
        assertFalse(PageViewPolicy.shouldReport("GET", 200, HTML, "/login", null));
        assertFalse(PageViewPolicy.shouldReport("GET", 200, HTML, "/login", ""));
        assertFalse(PageViewPolicy.shouldReport("GET", 200, HTML, "/login", "   "));
    }

    @Test
    void pagePathDropsQueryStringAndTrailingSlash() {
        assertEquals("/a/b", PageViewPolicy.pagePath("/a/b/?x=1"));
        assertEquals("/a/b", PageViewPolicy.pagePath("/a/b?x=1&y=2"));
        assertEquals("/a/b", PageViewPolicy.pagePath("/a/b#section"));
        assertEquals("/a/b", PageViewPolicy.pagePath("/a/b/"));
        assertEquals("/login", PageViewPolicy.pagePath("/login"));
    }

    @Test
    void pagePathKeepsRootAsRoot() {
        assertEquals("/", PageViewPolicy.pagePath("/"));
        assertEquals("/", PageViewPolicy.pagePath("/?next=game"));
        assertEquals("/", PageViewPolicy.pagePath("//"));
        assertEquals("/", PageViewPolicy.pagePath(""));
        assertEquals("/", PageViewPolicy.pagePath(null));
    }

    @Test
    void pagePathIsCappedAt200Characters() {
        String longPath = "/" + "a".repeat(200); // 201 characters
        String reported = PageViewPolicy.pagePath(longPath);
        assertEquals(200, reported.length());
        assertEquals(longPath.substring(0, 200), reported);
        // A path already at the cap is left alone.
        String exact = "/" + "b".repeat(199);
        assertEquals(exact, PageViewPolicy.pagePath(exact));
    }
}
