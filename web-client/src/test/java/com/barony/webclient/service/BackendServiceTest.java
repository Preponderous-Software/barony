package com.barony.webclient.service;

import com.barony.webclient.model.RunHistory;
import com.barony.webclient.model.RunRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Verifies BackendService proxies auth requests and forwards the auth cookie (not a bearer token)
 * on per-user game requests, and surfaces the backend's Set-Cookie on login/logout so the web
 * controller can relay it to the browser.
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
