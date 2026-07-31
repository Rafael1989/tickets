package com.ticketwave.booking.security;

import com.ticketwave.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BookingOwnershipTest {

    @Mock
    private BookingRepository bookingRepository;

    @Test
    void isOwnedBy_whenBookingBelongsToUsername_returnsTrue() {
        BookingOwnership bookingOwnership = new BookingOwnership(bookingRepository);
        given(bookingRepository.existsByIdAndUserUsername(500L, "alice")).willReturn(true);

        assertThat(bookingOwnership.isOwnedBy(500L, "alice")).isTrue();
    }

    @Test
    void isOwnedBy_whenBookingBelongsToSomeoneElse_returnsFalse() {
        BookingOwnership bookingOwnership = new BookingOwnership(bookingRepository);
        given(bookingRepository.existsByIdAndUserUsername(500L, "mallory")).willReturn(false);

        assertThat(bookingOwnership.isOwnedBy(500L, "mallory")).isFalse();
    }
}
