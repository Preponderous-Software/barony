package com.barony.backend.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Prunes idle guests periodically (default every 6 hours, first run 10 minutes after startup). */
@Configuration
@EnableScheduling
public class GuestPruneScheduler {

    private final GuestSessionService guestSessionService;

    public GuestPruneScheduler(GuestSessionService guestSessionService) {
        this.guestSessionService = guestSessionService;
    }

    @Scheduled(initialDelayString = "${guest.prune.initial-delay-ms:600000}",
               fixedDelayString = "${guest.prune.interval-ms:21600000}")
    public void prune() {
        guestSessionService.pruneIdleGuests();
    }
}
