package com.barony.webclient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the contract between the pages this web client serves and the web client itself: a page
 * is loaded from the web client's own origin, so every API call it makes has to be answered here
 * and proxied to the backend. A backend endpoint that gained no matching proxy still worked behind
 * the gateway deployment (where {@code /api/*} is routed straight to the backend) while 404ing in
 * the documented docker-compose setup — which is how {@code /api/session/runs} was missed (#82).
 *
 * The calls are read out of the rendered markup rather than listed here, so a call added later is
 * checked without this test being updated. Two things it deliberately does not see: a call whose
 * URL is built at runtime rather than written as a literal, and a mapping registered under a URL
 * pattern rather than a literal path (none of either exists today) — a pattern mapping added later
 * would show up here as a false failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProxyRouteCoverageTest {

    /** The pages this web client serves; each is rendered and read for the calls it makes. */
    private static final List<String> PAGES = List.of("/login", "/register", "/game");

    /** {@code fetch('/api/...'} — the form every call on those pages takes. */
    private static final Pattern FETCH_CALL = Pattern.compile("fetch\\('(/[^']*)'");

    /** {@code method: 'POST'} inside the options object that follows; absent means GET. */
    private static final Pattern METHOD_OPTION = Pattern.compile("method:\\s*'([A-Z]+)'");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApiCallThePagesMakeIsServedByTheWebClient() throws Exception {
        Set<String> served = servedRoutes();

        for (String page : PAGES) {
            List<String> calls = apiCallsIn(render(page));
            assertFalse(calls.isEmpty(),
                    "Expected " + page + " to call the API; no call was found, so this guard "
                            + "would pass vacuously for that page");
            for (String call : calls) {
                assertTrue(served.contains(call),
                        page + " calls " + call + ", but the web client serves no such route, so "
                                + "the browser gets a 404 instead of the backend's response. "
                                + "Routes served: " + served);
            }
        }
    }

    /**
     * Every API call a page makes, as "METHOD /path". The options object is looked for between one
     * call and the next, so a call's method is not read off a later one.
     */
    private List<String> apiCallsIn(String html) {
        List<String> calls = new ArrayList<>();
        Matcher fetches = FETCH_CALL.matcher(html);
        int previousEnd = -1;
        String previousPath = null;
        while (fetches.find()) {
            if (previousPath != null) {
                calls.add(methodBetween(html, previousEnd, fetches.start()) + " " + previousPath);
            }
            previousPath = fetches.group(1);
            previousEnd = fetches.end();
        }
        if (previousPath != null) {
            calls.add(methodBetween(html, previousEnd, html.length()) + " " + previousPath);
        }
        return calls;
    }

    private String methodBetween(String html, int from, int to) {
        Matcher method = METHOD_OPTION.matcher(html.substring(from, to));
        return method.find() ? method.group(1) : "GET";
    }

    /** Every route this web client answers, as "METHOD /path". */
    private Set<String> servedRoutes() {
        Set<String> routes = new LinkedHashSet<>();
        handlerMapping.getHandlerMethods().keySet().forEach(info -> {
            Set<String> methods = methodNames(info);
            info.getDirectPaths().forEach(path ->
                    methods.forEach(method -> routes.add(method + " " + path)));
        });
        return routes;
    }

    /** A mapping with no method condition answers every method, so name the ones the pages use. */
    private Set<String> methodNames(RequestMappingInfo info) {
        Set<String> methods = new LinkedHashSet<>();
        info.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
        if (methods.isEmpty()) {
            methods.add("GET");
            methods.add("POST");
        }
        return methods;
    }

    private String render(String page) throws Exception {
        return mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
