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
 * The calls are read out of the rendered markup, and out of every script the page loads from this
 * web client, rather than listed here, so a call added later is checked without this test being
 * updated. The battlefield ({@code /battle}) makes all of its calls from such a script, so the
 * markup alone would miss them (#143). Two things it deliberately does not see: a call whose
 * URL is built at runtime rather than written as a literal, and a mapping registered under a URL
 * pattern rather than a literal path (none of either exists today) — a pattern mapping added later
 * would show up here as a false failure.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProxyRouteCoverageTest {

    /** The pages this web client serves; each is rendered and read for the calls it makes. */
    private static final List<String> PAGES = List.of("/login", "/register", "/game", "/battle");

    /** {@code fetch('/api/...'} — the form every call on those pages takes. */
    private static final Pattern FETCH_CALL = Pattern.compile("fetch\\('(/[^']*)'");

    /** {@code method: 'POST'} inside the options object that follows; absent means GET. */
    private static final Pattern METHOD_OPTION = Pattern.compile("method:\\s*'([A-Z]+)'");

    /**
     * {@code <script src="...">} as rendered. Thymeleaf content-hashes the path (for example
     * {@code /js/battle-page-<md5>.js}), so the rendered URL is the one requested.
     */
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script\\b[^>]*\\bsrc=\"([^\"]+)\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApiCallThePagesMakeIsServedByTheWebClient() throws Exception {
        Set<String> served = servedRoutes();

        for (String page : PAGES) {
            String html = render(page);
            List<String> markupCalls = apiCallsIn(html);
            int callsFound = markupCalls.size();
            assertServed(served, page, markupCalls);
            for (String script : sameOriginScriptsIn(html)) {
                List<String> scriptCalls = apiCallsIn(fetchScript(script));
                callsFound += scriptCalls.size();
                assertServed(served, page + " (in " + script + ")", scriptCalls);
            }
            assertTrue(callsFound > 0,
                    "Expected " + page + " to call the API; no call was found in its markup or in "
                            + "the scripts it loads, so this guard would pass vacuously for that page");
        }
    }

    private void assertServed(Set<String> served, String caller, List<String> calls) {
        for (String call : calls) {
            assertTrue(served.contains(call),
                    caller + " calls " + call + ", but the web client serves no such route, so "
                            + "the browser gets a 404 instead of the backend's response. "
                            + "Routes served: " + served);
        }
    }

    /**
     * The scripts a page loads from this web client itself, i.e. a same-origin path. A script from
     * another origin ({@code https://...}, or protocol-relative {@code //...}) is not served here,
     * so it is skipped; none is loaded today.
     */
    private List<String> sameOriginScriptsIn(String html) {
        List<String> scripts = new ArrayList<>();
        Matcher tags = SCRIPT_SRC.matcher(html);
        while (tags.find()) {
            String src = tags.group(1);
            if (src.startsWith("/") && !src.startsWith("//")) {
                scripts.add(src);
            }
        }
        return scripts;
    }

    /**
     * Every API call in a page or script, as "METHOD /path". The options object is looked for
     * between one call and the next, so a call's method is not read off a later one. Only
     * {@code /api/} paths count: a script may also fetch a static file by a literal path (the
     * battlefield's engine fetches its own {@code .wasm}, though through a variable today), and
     * such a file is served as-is rather than proxied to the backend.
     */
    private List<String> apiCallsIn(String source) {
        List<String> calls = new ArrayList<>();
        Matcher fetches = FETCH_CALL.matcher(source);
        int previousEnd = -1;
        String previousPath = null;
        while (fetches.find()) {
            if (previousPath != null) {
                calls.add(methodBetween(source, previousEnd, fetches.start()) + " " + previousPath);
            }
            previousPath = fetches.group(1).startsWith("/api/") ? fetches.group(1) : null;
            previousEnd = fetches.end();
        }
        if (previousPath != null) {
            calls.add(methodBetween(source, previousEnd, source.length()) + " " + previousPath);
        }
        return calls;
    }

    private String methodBetween(String source, int from, int to) {
        Matcher method = METHOD_OPTION.matcher(source.substring(from, to));
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

    /** A script the page loads, requested by the (content-hashed) URL the page renders. */
    private String fetchScript(String src) throws Exception {
        return mockMvc.perform(get(src))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
