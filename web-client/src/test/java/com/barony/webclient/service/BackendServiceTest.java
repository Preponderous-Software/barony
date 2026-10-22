package com.barony.webclient.service;

import com.barony.webclient.model.GameState;
import com.barony.webclient.model.RulerDecision;
import com.barony.webclient.model.RulerStats;
import com.barony.webclient.model.RunHistory;
import com.barony.webclient.model.RunRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Verifies BackendService proxies auth requests and forwards the auth cookie (not a bearer token)
 * on per-user game requests, and surfaces the backend's Set-Cookie on login/logout so the web
 * controller can relay it to the browser. Also verifies the page's {@code X-Barony-Player} mode is
 * passed through, so the backend resolves account vs. guest the same way as behind the gateway.
 */
class BackendServiceTest {

    private static final String BACKEND = "http://localhost:8080";

    private BackendService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new BackendService(new RestTemplateBuilder());
        RestTemplate restTemplate = (RestTemplate) readField(service, "restTemplate");
        this.server = MockRestServiceServer.createServer(restTemplate);
        setField(service, "backendUrl", BACKEND);
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void loginReturnsResponseEntityCarryingSetCookie() {
        server.expect(requestTo(BACKEND + "/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("alice"))
                .andRespond(withSuccess("{\"username\":\"alice\"}", MediaType.APPLICATION_JSON)
                        .headers(setCookie("barony_token=jwt-abc; HttpOnly; Path=/")));

        ResponseEntity<Map> result = service.login(Map.of("username", "alice", "password", "password123"));

        assertEquals("alice", result.getBody().get("username"));
        assertTrue(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("barony_token=jwt-abc"));
        server.verify();
    }

    @Test
    void logoutForwardsCookieAndReturnsSetCookieThatClears() {
        server.expect(requestTo(BACKEND + "/api/auth/logout"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"message\":\"logged out\"}", MediaType.APPLICATION_JSON)
                        .headers(setCookie("barony_token=; Max-Age=0; Path=/")));

        ResponseEntity<Map> result = service.logout("barony_token=jwt-abc");

        assertTrue(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
        server.verify();
    }

    @Test
    void registerProxiesToBackendAuthEndpoint() {
        server.expect(requestTo(BACKEND + "/api/auth/register"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("alice"))
                .andRespond(withSuccess("{\"id\":1,\"username\":\"alice\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = service.register(Map.of("username", "alice", "password", "password123"));

        assertEquals("alice", result.get("username"));
        server.verify();
    }

    @Test
    void sessionStateForwardsAuthCookie() {
        server.expect(requestTo(BACKEND + "/api/session/state"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"width\":5,\"height\":5}", MediaType.APPLICATION_JSON));

        assertNotNull(service.getSessionState("barony_token=jwt-abc"));
        server.verify();
    }

    @Test
    void sessionCommandForwardsAuthCookie() {
        server.expect(requestTo(BACKEND + "/api/session/command"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"width\":5,\"height\":5}", MediaType.APPLICATION_JSON));

        assertNotNull(service.sessionCommand("barony_token=jwt-abc", new com.barony.webclient.model.Command()));
        server.verify();
    }

    @Test
    void sessionRunsForwardsAuthCookieAndRelaysTheWholeRecord() {
        server.expect(requestTo(BACKEND + "/api/session/runs"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"wins\":2,\"losses\":1,\"runs\":[{\"id\":7,"
                        + "\"username\":\"alice\",\"result\":\"WIN\",\"turnsPlayed\":42,"
                        + "\"castlesHeld\":3,\"castlesTotal\":4,\"villagesHeld\":5,"
                        + "\"villagesTotal\":6,\"armiesRemaining\":2,\"soldiersRemaining\":30,"
                        + "\"finishedAt\":\"2026-01-02T03:04:05Z\"}]}",
                        MediaType.APPLICATION_JSON));

        RunHistory history = service.sessionRuns("barony_token=jwt-abc");

        assertEquals(2, history.getWins());
        assertEquals(1, history.getLosses());
        assertEquals(1, history.getRuns().size());
        // Every field of the backend's record is modelled, so the proxy drops none of it.
        RunRecord run = history.getRuns().get(0);
        assertEquals(7L, run.getId());
        assertEquals("alice", run.getUsername());
        assertEquals("WIN", run.getResult());
        assertEquals(42, run.getTurnsPlayed());
        assertEquals(3, run.getCastlesHeld());
        assertEquals(4, run.getCastlesTotal());
        assertEquals(5, run.getVillagesHeld());
        assertEquals(6, run.getVillagesTotal());
        assertEquals(2, run.getArmiesRemaining());
        assertEquals(30, run.getSoldiersRemaining());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), run.getFinishedAt());
        server.verify();
    }

    @Test
    void sessionPreferencesForwardsAuthCookie() {
        server.expect(requestTo(BACKEND + "/api/session/preferences"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"settings\":{\"theme\":\"classic\"}}",
                        MediaType.APPLICATION_JSON));

        Map<String, Object> preferences = service.sessionPreferences("barony_token=jwt-abc");

        assertEquals(Map.of("theme", "classic"), preferences.get("settings"));
        server.verify();
    }

    @Test
    void savingPreferencesForwardsAuthCookieAndTheWholeBody() {
        server.expect(requestTo(BACKEND + "/api/session/preferences"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andExpect(jsonPath("$.panelLayout.order[0]").value("armies"))
                .andExpect(jsonPath("$.settings.theme").value("classic"))
                .andRespond(withSuccess("{\"settings\":{\"theme\":\"classic\"}}",
                        MediaType.APPLICATION_JSON));

        service.saveSessionPreferences("barony_token=jwt-abc", Map.of(
                "settings", Map.of("theme", "classic"),
                "panelLayout", Map.of("order", java.util.List.of("armies", "status"))));

        server.verify();
    }

    @Test
    void guestForwardsCookieAndReturnsTheGuestSetCookie() {
        server.expect(requestTo(BACKEND + "/api/guest"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "barony_guest=guest-1"))
                .andRespond(withSuccess("{\"guest\":true}", MediaType.APPLICATION_JSON)
                        .headers(setCookie("barony_guest=guest-1; HttpOnly; Path=/")));

        ResponseEntity<Map> result = service.guest("barony_guest=guest-1");

        assertEquals(true, result.getBody().get("guest"));
        assertTrue(result.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("barony_guest=guest-1"));
        server.verify();
    }

    @Test
    void claimGuestForwardsBothCookies() {
        server.expect(requestTo(BACKEND + "/api/guest/claim"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc; barony_guest=guest-1"))
                .andRespond(withSuccess("{\"claimed\":true}", MediaType.APPLICATION_JSON));

        ResponseEntity<Map> result = service.claimGuest("barony_token=jwt-abc; barony_guest=guest-1");

        assertEquals(true, result.getBody().get("claimed"));
        server.verify();
    }

    @Test
    void sessionTickForwardsAuthCookie() {
        server.expect(requestTo(BACKEND + "/api/session/tick"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"tickCount\":8}", MediaType.APPLICATION_JSON));

        assertEquals(8, service.sessionTick("barony_token=jwt-abc").getTickCount());
        server.verify();
    }

    @Test
    void sessionResetForwardsAuthCookie() {
        server.expect(requestTo(BACKEND + "/api/session/reset"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"tickCount\":0}", MediaType.APPLICATION_JSON));

        assertEquals(0, service.sessionReset("barony_token=jwt-abc").getTickCount());
        server.verify();
    }

    @Test
    void sessionDecisionForwardsAuthCookieAndTheDecision() {
        server.expect(requestTo(BACKEND + "/api/session/decision"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andExpect(jsonPath("$.category").value("ECONOMIC"))
                .andExpect(jsonPath("$.choice").value("HEAVY_TAXATION"))
                .andRespond(withSuccess("{\"economicPolicy\":\"HEAVY_TAXATION\"}",
                        MediaType.APPLICATION_JSON));

        RulerDecision decision = new RulerDecision();
        decision.setCategory(RulerDecision.PolicyCategory.ECONOMIC);
        decision.setChoice("HEAVY_TAXATION");

        GameState state = service.sessionChangePolicy("barony_token=jwt-abc", decision);

        assertEquals("HEAVY_TAXATION", state.getEconomicPolicy());
        server.verify();
    }

    @Test
    void sessionRulerStatsForwardsAuthCookieAndRelaysEveryField() {
        server.expect(requestTo(BACKEND + "/api/session/ruler-stats"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "barony_token=jwt-abc"))
                .andRespond(withSuccess("{\"averageStability\":61.5,\"averageMorale\":72.25,"
                        + "\"averageLoyalty\":80.0,\"totalPopulation\":340,"
                        + "\"economicPolicy\":\"BALANCED\",\"militaryPolicy\":\"DEFENSIVE\","
                        + "\"populationPolicy\":\"GROWTH\",\"ticksUntilNextDecision\":3}",
                        MediaType.APPLICATION_JSON));

        RulerStats stats = service.sessionRulerStats("barony_token=jwt-abc");

        assertEquals(61.5, stats.getAverageStability());
        assertEquals(72.25, stats.getAverageMorale());
        assertEquals(80.0, stats.getAverageLoyalty());
        assertEquals(340, stats.getTotalPopulation());
        assertEquals("BALANCED", stats.getEconomicPolicy());
        assertEquals("DEFENSIVE", stats.getMilitaryPolicy());
        assertEquals("GROWTH", stats.getPopulationPolicy());
        assertEquals(3, stats.getTicksUntilNextDecision());
        server.verify();
    }

    @Test
    void blankCookieIsNotForwarded() {
        server.expect(requestTo(BACKEND + "/api/session/state"))
                .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.getSessionState("  ");

        server.verify();
    }

    @Test
    void playerModeHeaderFromTheBrowserRequestIsForwarded() {
        MockHttpServletRequest browserRequest = new MockHttpServletRequest();
        browserRequest.addHeader(BackendService.PLAYER_MODE_HEADER, "guest");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(browserRequest));
        server.expect(requestTo(BACKEND + "/api/session/command"))
                .andExpect(header(BackendService.PLAYER_MODE_HEADER, "guest"))
                .andExpect(header(HttpHeaders.COOKIE, "barony_guest=guest-1"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.sessionCommand("barony_guest=guest-1", new com.barony.webclient.model.Command());

        server.verify();
    }

    @Test
    void playerModeHeaderIsOmittedWhenTheBrowserSentNone() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        server.expect(requestTo(BACKEND + "/api/session/state"))
                .andExpect(headerDoesNotExist(BackendService.PLAYER_MODE_HEADER))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.getSessionState("barony_token=jwt-abc");

        server.verify();
    }

    @Test
    void blankPlayerModeHeaderIsNotForwarded() {
        MockHttpServletRequest browserRequest = new MockHttpServletRequest();
        browserRequest.addHeader(BackendService.PLAYER_MODE_HEADER, " ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(browserRequest));
        server.expect(requestTo(BACKEND + "/api/session/state"))
                .andExpect(headerDoesNotExist(BackendService.PLAYER_MODE_HEADER))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        service.getSessionState("barony_token=jwt-abc");

        server.verify();
    }

    private static HttpHeaders setCookie(String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, value);
        return headers;
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
