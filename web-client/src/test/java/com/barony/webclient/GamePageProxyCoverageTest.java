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
 * Guards the contract between the game page and the web client that serves it: the page is served
 * from the web client's own origin, so every `/api/*` call it makes has to be answered here and
 * proxied to the backend. A backend endpoint that gained no matching proxy still worked behind the
 * gateway deployment (where `/api/*` is routed straight to the backend) while 404ing in the
 * documented `docker-compose` setup — which is how `/api/session/runs` was missed (#82).
 *
 * The page's calls are read out of the rendered markup rather than listed here, so a call added
 * later is checked without this test being updated.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GamePageProxyCoverageTest {

    /** `fetch('/api/...'` — the form every call on the game page takes. */
    private static final Pattern FETCH_CALL = Pattern.compile("fetch\\('(/[^']*)'");

    /** `method: 'POST'` inside the options object that follows; absent means GET. */
    private static final Pattern METHOD_OPTION = Pattern.compile("method:\\s*'([A-Z]+)'");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApiCallTheGamePageMakesIsServedByTheWebClient() throws Exception {
        List<String> calls = apiCallsIn(renderGamePage());
        Set<String> served = servedRoutes();

        assertFalse(calls.isEmpty(),
                "Expected the game page to call the API; none were found, so this guard would "
                        + "pass vacuously");
        for (String call : calls) {
            assertTrue(served.contains(call),
                    "The game page calls " + call + ", but the web client serves no such route, "
                            + "so the browser gets a 404 instead of the backend's response. "
                            + "Routes served: " + served);
        }
    }

    /**
     * Every API call the page makes, as "METHOD /path". The options object is looked for between
     * one call and the next, so a call's method is not read off a later one.
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

    /** A mapping with no method condition answers every method, so name the ones the page uses. */
    private Set<String> methodNames(RequestMappingInfo info) {
        Set<String> methods = new LinkedHashSet<>();
        info.getMethodsCondition().getMethods().forEach(method -> methods.add(method.name()));
        if (methods.isEmpty()) {
            methods.add("GET");
            methods.add("POST");
        }
        return methods;
    }

    private String renderGamePage() throws Exception {
        return mockMvc.perform(get("/game"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
