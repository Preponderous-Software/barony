package com.barony.webclient.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the startup and page-view reports against a stub trace server on a loopback port (the
 * JDK's own HTTP server), so nothing here ever reaches the real service.
 */
class UsageReportingServiceTest {

    private HttpServer server;
    private String endpoint;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final CountDownLatch arrived = new CountDownLatch(1);

    @BeforeEach
    void startStub() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(readAll(exchange.getRequestBody()));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
            arrived.countDown();
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    @Test
    void startupEventCarriesProgramNameVersionAndServiceTag() throws Exception {
        UsageReportingService service = new UsageReportingService(true, endpoint, "test-key", "9.9.9-TEST");
        assertTrue(service.isEnabled());

        service.reportStartup();

        assertTrue(arrived.await(5, TimeUnit.SECONDS), "startup event was not delivered");
        assertEquals(1, bodies.size());
        assertEquals("{\"application\":\"barony\",\"name\":\"startup\","
                + "\"tags\":{\"version\":\"9.9.9-TEST\",\"service\":\"true\"}}", bodies.get(0));
        assertEquals("Bearer test-key", authorizations.get(0));
        service.close();
    }

    @Test
    void pageViewEventCarriesPathAndVersionOnly() throws Exception {
        UsageReportingService service = new UsageReportingService(true, endpoint, "test-key", "9.9.9-TEST");

        service.reportPageView("/game");

        assertTrue(arrived.await(5, TimeUnit.SECONDS), "page-view event was not delivered");
        assertEquals(1, bodies.size());
        assertEquals("{\"application\":\"barony\",\"name\":\"page-view\","
                + "\"tags\":{\"page\":\"/game\",\"version\":\"9.9.9-TEST\"}}", bodies.get(0));
        assertEquals("Bearer test-key", authorizations.get(0));
        service.close();
    }

    @Test
    void startupTagsAreOnlyVersionAndService() {
        UsageReportingService service = new UsageReportingService(false, endpoint, "k", "1.2.3");
        Map<String, String> tags = service.startupTags();
        assertEquals(Map.of("version", "1.2.3", "service", "true"), tags);
        service.close();
    }

    @Test
    void blankVersionIsReportedAsUnknown() {
        UsageReportingService service = new UsageReportingService(false, endpoint, "k", " ");
        assertEquals("unknown", service.startupTags().get("version"));
        service.close();
    }

    @Test
    void disabledSendsNothing() throws Exception {
        UsageReportingService service = new UsageReportingService(false, endpoint, "test-key", "1.0");
        assertFalse(service.isEnabled());

        service.reportStartup();
        service.reportPageView("/game");

        assertFalse(arrived.await(300, TimeUnit.MILLISECONDS));
        assertTrue(bodies.isEmpty());
        service.close();
    }

    @Test
    void missingKeySendsNothing() throws Exception {
        UsageReportingService service = new UsageReportingService(true, endpoint, "", "1.0");
        assertFalse(service.isEnabled());

        service.reportStartup();
        service.reportPageView("/game");

        assertFalse(arrived.await(300, TimeUnit.MILLISECONDS));
        assertTrue(bodies.isEmpty());
        service.close();
    }

    @Test
    void blankEndpointIsDisabledRatherThanFailing() {
        UsageReportingService service = assertDoesNotThrow(
                () -> new UsageReportingService(true, " ", "test-key", "1.0"));
        assertFalse(service.isEnabled());
        service.close();
    }

    @Test
    void unreachableServerNeverThrows() {
        // Nothing listens on this port once the stub is stopped; the report must be dropped quietly.
        server.stop(0);
        UsageReportingService service = new UsageReportingService(true, endpoint, "test-key", "1.0");
        assertDoesNotThrow(service::reportStartup);
        assertDoesNotThrow(() -> service.reportPageView("/game"));
        assertDoesNotThrow(service::close);
    }

    private static String readAll(InputStream in) throws java.io.IOException {
        try (InputStream stream = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
