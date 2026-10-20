package com.barony.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The scheduler is only a trigger: each run hands off to {@link GuestSessionService#pruneIdleGuests()},
 * on the documented default cadence (first run 10 minutes after startup, then every 6 hours), with
 * both delays overridable by property. The prune logic itself is covered by {@link GuestPruneTest}.
 */
class GuestPruneSchedulerTest {

    @Test
    void pruneDelegatesToTheGuestSessionService() {
        GuestSessionService guestSessionService = mock(GuestSessionService.class);
        when(guestSessionService.pruneIdleGuests()).thenReturn(3);

        new GuestPruneScheduler(guestSessionService).prune();

        verify(guestSessionService, times(1)).pruneIdleGuests();
        verifyNoMoreInteractions(guestSessionService);
    }

    @Test
    void eachRunPrunesAgain() {
        GuestSessionService guestSessionService = mock(GuestSessionService.class);
        GuestPruneScheduler scheduler = new GuestPruneScheduler(guestSessionService);

        scheduler.prune();
        scheduler.prune();

        verify(guestSessionService, times(2)).pruneIdleGuests();
    }

    @Test
    void scheduleDefaultsToTenMinutesThenEverySixHours() throws NoSuchMethodException {
        Scheduled scheduled = GuestPruneScheduler.class.getMethod("prune").getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "prune() must be @Scheduled or idle guests are never removed");
        // 600000 ms = 10 minutes; 21600000 ms = 6 hours
        assertEquals("${guest.prune.initial-delay-ms:600000}", scheduled.initialDelayString());
        assertEquals("${guest.prune.interval-ms:21600000}", scheduled.fixedDelayString());
    }

    @Test
    void schedulingIsEnabledByTheSchedulerItself() {
        assertTrue(GuestPruneScheduler.class.isAnnotationPresent(EnableScheduling.class),
                "without @EnableScheduling the @Scheduled prune never fires");
    }
}
