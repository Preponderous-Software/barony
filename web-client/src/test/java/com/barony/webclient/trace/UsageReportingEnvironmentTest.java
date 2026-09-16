package com.barony.webclient.trace;

import com.barony.webclient.service.UsageReportingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The environment opt-outs shared by every program that reports to trace must win over this
 * service's own {@code usage-reporting.enabled} setting. The vendored client checks them in its
 * builder, so this only holds while {@link UsageReportingService} routes its switch through the
 * builder instead of deciding beforehand. Lives in this package to reach the client's
 * package-private environment seam; the endpoint is a closed loopback name, so nothing is sent.
 */
class UsageReportingEnvironmentTest {

    private static final String ENDPOINT = "http://127.0.0.1:9";

    private final Map<String, String> environment = new HashMap<>();
    private Function<String, String> realEnvironment;

    @BeforeEach
    void isolateEnvironment() {
        realEnvironment = TraceClient.environment;
        TraceClient.environment = environment::get;
    }

    @AfterEach
    void restoreEnvironment() {
        TraceClient.environment = realEnvironment;
    }

    @Test
    void doNotTrackDisablesReportingEvenWhenTheSettingSaysEnabled() {
        environment.put("DO_NOT_TRACK", "1");
        UsageReportingService service = new UsageReportingService(true, ENDPOINT, "test-key", "1.0");
        assertFalse(service.isEnabled(), "DO_NOT_TRACK=1 must switch reporting off");
        service.close();
    }

    @Test
    void traceUsageReportingOffDisablesReportingEvenWhenTheSettingSaysEnabled() {
        environment.put("TRACE_USAGE_REPORTING", "off");
        UsageReportingService service = new UsageReportingService(true, ENDPOINT, "test-key", "1.0");
        assertFalse(service.isEnabled(), "TRACE_USAGE_REPORTING=off must switch reporting off");
        service.close();
    }

    @Test
    void anEmptyEnvironmentLeavesTheSettingInCharge() {
        UsageReportingService on = new UsageReportingService(true, ENDPOINT, "test-key", "1.0");
        assertTrue(on.isEnabled());
        on.close();
        UsageReportingService off = new UsageReportingService(false, ENDPOINT, "test-key", "1.0");
        assertFalse(off.isEnabled());
        off.close();
    }
}
