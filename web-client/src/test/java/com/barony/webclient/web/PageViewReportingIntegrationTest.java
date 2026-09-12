package com.barony.webclient.web;

import com.barony.webclient.service.BackendService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives page-view reporting end to end through the real web stack -- interceptor, Thymeleaf,
 * static resource handling -- against a stub trace server on a loopback port (the JDK's own HTTP
 * server), so nothing here ever reaches the real service. The other suites run with reporting off
 * (see {@code src/test/resources/application.properties}); this one turns it on for its own
 * context and points it at the stub.
 */
@SpringBootTest(properties = {
        "usage-reporting.enabled=true",
        "usage-reporting.key=test-key",
        "barony.version=9.9.9-TEST"})
@AutoConfigureMockMvc
class PageViewReportingIntegrationTest {

    private static final String BROWSER =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/128.0.0.0 Safari/537.36";

    private static HttpServer server;
    private static final List<String> bodies = new CopyOnWriteArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    /** The game page itself makes no backend call while rendering; this only keeps the context off the network. */
    @MockBean
    private BackendService backendService;

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            bodies.add(readAll(exchange.getRequestBody()));
            exchange.sendResponseHeaders(201, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    @DynamicPropertySource
    static void pointReportingAtStub(DynamicPropertyRegistry registry) {
        registry.add("usage-reporting.endpoint",
                () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @BeforeEach
    void forgetEarlierEvents() throws InterruptedException {
        // The startup event (and page views from an earlier test method) may still be in flight;
        // let them land before the slate is wiped so they cannot be mistaken for this test's own.
        Thread.sleep(200);
        bodies.clear();
    }

    @Test
    void servedPageIsReportedOnceWithItsPathOnly() throws Exception {
        mockMvc.perform(get("/login?next=game").header(HttpHeaders.USER_AGENT, BROWSER))
                .andExpect(status().isOk());

        List<String> pageViews = awaitPageViews(1);
        assertEquals(1, pageViews.size(), "expected exactly one page-view, got: " + bodies);
        assertEquals("{\"application\":\"barony\",\"name\":\"page-view\","
                + "\"tags\":{\"page\":\"/login\",\"version\":\"9.9.9-TEST\"}}", pageViews.get(0));
    }

    @Test
    void staticResourceBotAndNotFoundProduceNothing() throws Exception {
        // A real, existing asset -- served fine, but not a page.
        mockMvc.perform(get("/css/style.css").header(HttpHeaders.USER_AGENT, BROWSER))
                .andExpect(status().isOk());
        // A real page asked for by a crawler.
        mockMvc.perform(get("/login").header(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"))
                .andExpect(status().isOk());
        // Nothing lives here.
        mockMvc.perform(get("/no-such-page").header(HttpHeaders.USER_AGENT, BROWSER))
                .andExpect(status().isNotFound());

        // Give a wrongly queued report every chance to arrive before concluding it did not.
        Thread.sleep(500);
        assertTrue(pageViews().isEmpty(), "unexpected page-view(s): " + bodies);
    }

    /** Page views are sent off-thread; poll (bounded) rather than assert immediately. */
    private List<String> awaitPageViews(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pageViews().size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        return pageViews();
    }

    private static List<String> pageViews() {
        return bodies.stream()
                .filter(body -> body.contains("\"name\":\"page-view\""))
                .collect(Collectors.toList());
    }

    private static String readAll(InputStream in) throws IOException {
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
