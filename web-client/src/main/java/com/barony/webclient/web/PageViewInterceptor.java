package com.barony.webclient.web;

import com.barony.webclient.service.UsageReportingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Counts served HTML pages as {@code page-view} events. The decision is taken in
 * {@code afterCompletion}, once the response is written, because that is the only point where the
 * status and the {@code Content-Type} Thymeleaf set are both known -- a redirect or an error page
 * looks like any other request before then. Only the request path and the app version leave the
 * process; see {@link PageViewPolicy} for what is left out and why.
 */
public class PageViewInterceptor implements HandlerInterceptor {

    private final UsageReportingService usageReporting;

    public PageViewInterceptor(UsageReportingService usageReporting) {
        this.usageReporting = usageReporting;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // The reporting client is a no-op when disabled, but there is no reason to even look at
        // the request in that case.
        if (!usageReporting.isEnabled()) {
            return;
        }
        String page = PageViewPolicy.pagePath(request.getRequestURI());
        if (PageViewPolicy.shouldReport(request.getMethod(), response.getStatus(),
                response.getContentType(), page, request.getHeader(HttpHeaders.USER_AGENT))) {
            usageReporting.reportPageView(page);
        }
    }
}
