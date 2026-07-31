package com.ticketwave.booking.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * onCreate() is a JPA @PrePersist callback, only ever invoked by Hibernate
 * during a real INSERT — every other test in the suite builds a Booking
 * directly via the Lombok builder, which never triggers it. Package-private
 * access lets this test (same package, different source root) call it
 * directly without needing real persistence.
 */
class BookingTest {

    @Test
    void onCreate_whenBookingTimeUnset_defaultsToNow() {
        Booking booking = Booking.builder().build();

        booking.onCreate();

        assertThat(booking.getBookingTime()).isNotNull();
        assertThat(booking.getBookingTime()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void onCreate_whenBookingTimeAlreadySet_leavesItUnchanged() {
        Instant explicit = Instant.parse("2020-01-01T00:00:00Z");
        Booking booking = Booking.builder().bookingTime(explicit).build();

        booking.onCreate();

        assertThat(booking.getBookingTime()).isEqualTo(explicit);
    }
}
