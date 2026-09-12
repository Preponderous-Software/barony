package com.barony.webclient.service;

import com.barony.webclient.trace.TraceClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reports the web client's usage to the trace usage service
 * (https://github.com/Stephenson-Software/trace), so the operator can see which deployments of
 * Barony are running, on what version, and how often its pages are opened.
 *
 * <p>Two kinds of event are sent, both under the program name {@code barony} (the same name the
 * backend reports under; the two are told apart by their tags):
 *
 * <ul>
 *   <li>{@code startup}, once the application is ready, carrying the web client's version and the
 *       static tag {@code service=true} -- exactly as the backend does.</li>
 *   <li>{@code page-view}, one per HTML page served, carrying the request path and the version and
 *       nothing else. Which requests count is decided by {@link com.barony.webclient.web.PageViewPolicy};
 *       no IP address, user agent, cookie, session, account or referrer is ever recorded, so the
 *       result is a count of page loads, not of visitors.</li>
 * </ul>
 *
 * <p>Every send happens on the client's own daemon thread, never throws, and never slows a request
 * or startup; a trace server that is down or unreachable costs nothing beyond a dropped report.
 *
 * <p>Configured through the {@code usage-reporting.*} properties in {@code application.yml} (each
 * with an environment-variable override): {@code enabled} (default {@code true}), {@code endpoint}
 * and {@code key}. Setting {@code USAGE_REPORTING_ENABLED=false} turns both events off together.
 */
@Service
public class UsageReportingService {

    private static final Logger log = LoggerFactory.getLogger(UsageReportingService.class);

    /** The {@code application} the program key was issued for. */
    static final String APPLICATION = "barony";
    static final String STARTUP_EVENT = "startup";
    static final String PAGE_VIEW_EVENT = "page-view";

    private final TraceClient client;
    private final String version;

    public UsageReportingService(
            @Value("${usage-reporting.enabled:true}") boolean enabled,
            @Value("${usage-reporting.endpoint:https://trace.danielstephenson.dev}") String endpoint,
            @Value("${usage-reporting.key:}") String key,
            @Value("${barony.version:unknown}") String version) {
        this.version = version == null || version.isBlank() ? "unknown" : version;
        this.client = buildClient(enabled, endpoint, key);
        if (client.isEnabled()) {
            log.info("Usage reporting is on: barony sends a startup event (program name, version and"
                    + " service=true only) and one page-view event per HTML page served (path and"
                    + " version only) to {}. Turn it off with USAGE_REPORTING_ENABLED=false"
                    + " (usage-reporting.enabled).", endpoint);
        } else {
            log.info("Usage reporting is off.");
        }
    }

    private static TraceClient buildClient(boolean enabled, String endpoint, String key) {
        if (!enabled || endpoint == null || endpoint.isBlank()) {
            return TraceClient.disabled();
        }
        return TraceClient.builder(endpoint, APPLICATION)
                .key(key)
                .enabled(true)
                .logger(java.util.logging.Logger.getLogger(UsageReportingService.class.getName()))
                .build();
    }

    /** Whether reports will actually be sent (false when disabled or without a key). */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /** The tags attached to the startup event: the web client's version and {@code service=true}. */
    Map<String, String> startupTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("version", version);
        tags.put("service", "true");
        return tags;
    }

    /** The tags attached to a page view: the normalised request path and the web client's version. */
    Map<String, String> pageViewTags(String page) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("page", page);
        tags.put("version", version);
        return tags;
    }

    /** Sends the one {@code startup} event once the application is ready to serve requests. */
    @EventListener(ApplicationReadyEvent.class)
    public void reportStartup() {
        client.report(STARTUP_EVENT, null, startupTags());
    }

    /**
     * Sends one {@code page-view} event for {@code page}, an already-normalised request path (see
     * {@link com.barony.webclient.web.PageViewPolicy#pagePath}). Returns at once; the send is queued
     * on the client's thread, so calling this from a request never delays the response.
     */
    public void reportPageView(String page) {
        client.report(PAGE_VIEW_EVENT, null, pageViewTags(page));
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
