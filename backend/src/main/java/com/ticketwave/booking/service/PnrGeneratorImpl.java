package com.ticketwave.booking.service;

import com.ticketwave.booking.repository.BookingRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates a 6-character PNR and pre-checks it against BookingRepository to
 * make a collision vanishingly unlikely (32^6 ≈ 1.07 billion combinations).
 * The database's UNIQUE constraint on bookings.pnr remains the actual
 * correctness guarantee: if this pre-check ever races with a concurrent
 * insert of the same code, the booking creation's INSERT fails cleanly on
 * the constraint rather than silently double-issuing a PNR, and the caller
 * can simply retry the request. A mid-transaction catch-and-regenerate loop
 * was deliberately not built here — at this collision probability, it would
 * add real complexity (safely resuming a transaction that also holds seat
 * locks) for a failure mode this rare.
 */
@Component
public class PnrGeneratorImpl implements PnrGenerator {

    // Excludes 0/O and 1/I so a printed or read-aloud PNR isn't ambiguous.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final int MAX_ATTEMPTS = 10;

    private final BookingRepository bookingRepository;
    private final SecureRandom random = new SecureRandom();

    public PnrGeneratorImpl(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = randomCode();
            if (!bookingRepository.existsByPnr(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique PNR after " + MAX_ATTEMPTS + " attempts");
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
