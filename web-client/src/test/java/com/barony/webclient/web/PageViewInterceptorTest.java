package com.barony.webclient.web;

import com.barony.webclient.service.UsageReportingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the interceptor's own wiring -- the enabled check, and handing the request facts to
 * {@link PageViewPolicy} -- with the reporting service mocked out. The rules themselves are pinned
 * down in {@link PageViewPolicyTest}; the end-to-end path through the web stack is in
 * {@link PageViewReportingIntegrationTest}.
 */
class PageViewInterceptorTest {

    private static final String BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/128.0.0.0 Safari/537.36";

    private UsageReportingService usageReporting;
    private PageViewInterceptor interceptor;

    @BeforeEach
    void setUp() {
        usageReporting = mock(UsageReportingService.class);
        interceptor = new PageViewInterceptor(usageReporting);
    }

    @Test
    void disabledReportingNeverLooksAtTheRequest() {
        when(usageReporting.isEnabled()).thenReturn(false);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        interceptor.afterCompletion(request, response, new Object(), null);

        verifyNoInteractions(request, response);
        verify(usageReporting, never()).reportPageView(anyString());
    }

    @Test
    void servedPageIsReportedWithItsNormalisedPath() {
        when(usageReporting.isEnabled()).thenReturn(true);

        interceptor.afterCompletion(browserGet("/login/"), htmlResponse(200), new Object(), null);

        verify(usageReporting).reportPageView("/login");
    }

    @Test
    void requestThePolicyRejectsIsNotReported() {
        when(usageReporting.isEnabled()).thenReturn(true);

        MockHttpServletRequest post = browserGet("/login");
        post.setMethod("POST");
        interceptor.afterCompletion(post, htmlResponse(200), new Object(), null);
        interceptor.afterCompletion(browserGet("/"), htmlResponse(302), new Object(), null);
        interceptor.afterCompletion(browserGet("/game"), jsonResponse(), new Object(), null);
        interceptor.afterCompletion(browserGet("/css/style.css"), htmlResponse(200), new Object(), null);

        MockHttpServletRequest crawler = new MockHttpServletRequest("GET", "/login");
        crawler.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; Googlebot/2.1)");
        interceptor.afterCompletion(crawler, htmlResponse(200), new Object(), null);

        verify(usageReporting, never()).reportPageView(anyString());
    }

    @Test
    void requestWithoutUserAgentIsNotReported() {
        when(usageReporting.isEnabled()).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");

        interceptor.afterCompletion(request, htmlResponse(200), new Object(), null);

        verify(usageReporting, never()).reportPageView(anyString());
    }

    private static MockHttpServletRequest browserGet(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.addHeader(HttpHeaders.USER_AGENT, BROWSER);
        return request;
    }

    private static MockHttpServletResponse htmlResponse(int status) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        return response;
    }

    private static MockHttpServletResponse jsonResponse() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setContentType("application/json");
        return response;
    }
}
