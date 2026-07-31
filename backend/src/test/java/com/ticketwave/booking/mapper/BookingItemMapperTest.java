package com.ticketwave.booking.mapper;

import com.ticketwave.booking.dto.BookingItemRequest;
import com.ticketwave.booking.dto.BookingItemResponse;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingItem;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.user.entity.Passenger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BookingItemMapperTest {

    private final BookingItemMapper mapper = new BookingItemMapperImpl();

    @Test
    void toEntity_setsRelationsAndIgnoresServerComputedFare() {
        Booking booking = Booking.builder().id(500L).build();
        Seat seat = Seat.builder().id(2L).build();
        Passenger passenger = Passenger.builder().id(100L).build();
        BookingItemRequest request = new BookingItemRequest(500L, 2L, 100L);

        BookingItem item = mapper.toEntity(request, booking, seat, passenger);

        assertThat(item.getBooking()).isEqualTo(booking);
        assertThat(item.getSeat()).isEqualTo(seat);
        assertThat(item.getPassenger()).isEqualTo(passenger);
        assertThat(item.getId()).isNull();
        assertThat(item.getFare()).isNull();
    }

    @Test
    void toEntity_whenEveryArgumentIsNull_returnsNull() {
        assertThat(mapper.toEntity(null, null, null, null)).isNull();
    }

    @Test
    void toEntity_whenOnlyBookingIsNonNull_stillBuildsAnItem() {
        Booking booking = Booking.builder().id(500L).build();

        BookingItem item = mapper.toEntity(null, booking, null, null);

        assertThat(item.getBooking()).isEqualTo(booking);
    }

    @Test
    void toEntity_whenOnlySeatIsNonNull_stillBuildsAnItem() {
        Seat seat = Seat.builder().id(2L).build();

        BookingItem item = mapper.toEntity(null, null, seat, null);

        assertThat(item.getSeat()).isEqualTo(seat);
    }

    @Test
    void toEntity_whenOnlyPassengerIsNonNull_stillBuildsAnItem() {
        Passenger passenger = Passenger.builder().id(100L).build();

        BookingItem item = mapper.toEntity(null, null, null, passenger);

        assertThat(item.getPassenger()).isEqualTo(passenger);
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenRelationsAreNull_leavesFlattenedIdsNull() {
        BookingItem item = BookingItem.builder()
                .id(1L)
                .fare(new BigDecimal("30.00"))
                .build();

        BookingItemResponse response = mapper.toResponse(item);

        assertThat(response.bookingId()).isNull();
        assertThat(response.seatId()).isNull();
        assertThat(response.passengerId()).isNull();
    }

    @Test
    void toResponse_flattensBookingSeatAndPassengerIds() {
        BookingItem item = BookingItem.builder()
                .id(1L)
                .booking(Booking.builder().id(500L).build())
                .seat(Seat.builder().id(2L).build())
                .passenger(Passenger.builder().id(100L).build())
                .fare(new BigDecimal("30.00"))
                .build();

        BookingItemResponse response = mapper.toResponse(item);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.bookingId()).isEqualTo(500L);
        assertThat(response.seatId()).isEqualTo(2L);
        assertThat(response.passengerId()).isEqualTo(100L);
        assertThat(response.fare()).isEqualByComparingTo("30.00");
    }
}
