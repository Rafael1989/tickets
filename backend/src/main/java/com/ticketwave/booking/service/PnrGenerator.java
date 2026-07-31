package com.ticketwave.booking.service;

public interface PnrGenerator {

    /**
     * Returns a PNR not currently in use by any booking.
     */
    String generate();
}
