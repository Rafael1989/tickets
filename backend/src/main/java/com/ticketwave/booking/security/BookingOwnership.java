package com.ticketwave.booking.security;

import com.ticketwave.booking.repository.BookingRepository;
import org.springframework.stereotype.Component;

/**
 * SpEL-callable ownership check for @PreAuthorize on booking-scoped
 * operations (payments, refunds): lets a customer act on their own booking
 * while support/admin bypass it via a role check on the same expression.
 */
@Component("bookingOwnership")
public class BookingOwnership {

    private final BookingRepository bookingRepository;

    public BookingOwnership(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public boolean isOwnedBy(Long bookingId, String username) {
        return bookingRepository.existsByIdAndUserUsername(bookingId, username);
    }
}
