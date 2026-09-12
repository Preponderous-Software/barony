package com.barony.backend.service;

import com.barony.backend.trace.TraceClient;
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
 * Reports that the backend started to the trace usage service
 * (https://github.com/Stephenson-Software/trace), so the operator can see which deployments of
 * Barony are running and on what version.
 *
 * <p>Exactly one event is sent, {@code startup}, once the application is ready. It carries the
 * program name ({@code barony}), the backend version and the static tag {@code service=true};
 * nothing per request, and nothing about players, accounts, saved games or the host. The send
 * happens on the client's own daemon thread, never throws, and a trace server that is down or
 * unreachable costs nothing beyond a dropped report.
 *
 * <p>Configured through the {@code usage-reporting.*} properties in {@code application.properties}
 * (each with an environment-variable override): {@code enabled} (default {@code true}),
 * {@code endpoint} and {@code key}. Setting {@code USAGE_REPORTING_ENABLED=false} turns it off.
 */
@Service
public class UsageReportingService {

    private static final Logger log = LoggerFactory.getLogger(UsageReportingService.class);

    /** The {@code application} the program key was issued for. */
    static final String APPLICATION = "barony";
    static final String STARTUP_EVENT = "startup";

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
                    + " service=true only) to {}. Turn it off with USAGE_REPORTING_ENABLED=false"
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

    /** Whether a startup report will actually be sent (false when disabled or without a key). */
    public boolean isEnabled() {
        return client.isEnabled();
    }

    /** The tags attached to the startup event: the backend version and {@code service=true}. */
    Map<String, String> startupTags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("version", version);
        tags.put("service", "true");
        return tags;
    }

    /** Sends the one {@code startup} event once the application is ready to serve requests. */
    @EventListener(ApplicationReadyEvent.class)
    public void reportStartup() {
        client.report(STARTUP_EVENT, null, startupTags());
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
