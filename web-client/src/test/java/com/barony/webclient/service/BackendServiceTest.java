package com.barony.webclient.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * First test source for the web-client module. Verifies that BackendService proxies auth requests
 * and forwards the bearer token on per-user game requests to the backend.
 */
class BackendServiceTest {

    private static final String BACKEND = "http://localhost:8080";

    private BackendService service;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() throws Exception {
        this.service = new BackendService(new RestTemplateBuilder());
        // Bind the mock server to the RestTemplate the service built, and set the backend URL
        // (the @Value field is not injected outside a Spring context).
        RestTemplate restTemplate = (RestTemplate) readField(service, "restTemplate");
        this.server = MockRestServiceServer.createServer(restTemplate);
        setField(service, "backendUrl", BACKEND);
    }

    @Test
    void loginProxiesToBackendAuthEndpoint() {
        server.expect(requestTo(BACKEND + "/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("alice"))
                .andRespond(withSuccess("{\"token\":\"jwt-abc\",\"tokenType\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        Map<String, Object> result = service.login(Map.of("username", "alice", "password", "password123"));

        assertEquals("jwt-abc", result.get("token"));
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
    void sessionCommandForwardsBearerToken() {
        server.expect(requestTo(BACKEND + "/api/session/command"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-abc"))
                .andRespond(withSuccess("{\"width\":5,\"height\":5}", MediaType.APPLICATION_JSON));

        assertNotNull(service.sessionCommand("jwt-abc", new com.barony.webclient.model.Command()));
        server.verify();
    }

    @Test
    void logoutForwardsBearerTokenToBackend() {
        server.expect(requestTo(BACKEND + "/api/auth/logout"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-abc"))
                .andRespond(withSuccess("{\"message\":\"logged out\"}", MediaType.APPLICATION_JSON));

        service.logout("jwt-abc");

        server.verify();
    }

    @Test
    void sessionStateForwardsBearerToken() {
        server.expect(requestTo(BACKEND + "/api/session/state"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer jwt-abc"))
                .andRespond(withSuccess("{\"width\":5,\"height\":5}", MediaType.APPLICATION_JSON));

        assertNotNull(service.getSessionState("jwt-abc"));
        server.verify();
    }

    @Test
    void sessionTickForwardsBearerToken() {
        server.expect(requestTo(BACKEND + "/api/session/tick"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-abc"))
                .andRespond(withSuccess("{\"width\":5,\"height\":5}", MediaType.APPLICATION_JSON));

        assertNotNull(service.sessionTick("jwt-abc"));
        server.verify();
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
