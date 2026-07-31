package com.ticketwave.catalog.scheduling;

import com.ticketwave.catalog.service.SeatHoldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Backstop for seats whose hold expires without anyone ever retrying to hold
 * that specific seat again (the on-access reclaim in SeatHoldServiceImpl
 * only fires when a seat is actually requested) — without this, an expired
 * hold could sit as HELD indefinitely and undercount availability.
 */
@Component
public class SeatHoldExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldExpirationScheduler.class);

    private final SeatHoldService seatHoldService;

    public SeatHoldExpirationScheduler(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @Scheduled(fixedDelayString = "${ticketwave.inventory.hold-sweep-interval-ms:60000}")
    public void releaseExpiredHolds() {
        int released = seatHoldService.releaseExpiredHolds();
        if (released > 0) {
            log.info("Released {} expired seat hold(s)", released);
        }
    }
}
