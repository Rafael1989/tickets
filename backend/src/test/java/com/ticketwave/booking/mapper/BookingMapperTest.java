package com.ticketwave.booking.mapper;

import com.ticketwave.booking.dto.BookingRequest;
import com.ticketwave.booking.dto.BookingResponse;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.pricing.entity.DiscountType;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real MapStruct-generated BookingMapperImpl directly — every
 * *ServiceImplTest mocks BookingMapper, so without a test like this the
 * generated mapping code itself has zero coverage.
 */
class BookingMapperTest {

    private final BookingMapper mapper = new BookingMapperImpl();

    @Test
    void toEntity_setsUserAndScheduleAndIgnoresServerControlledFields() {
        User user = User.builder().id(1L).username("alice").build();
        Schedule schedule = Schedule.builder().id(10L).build();
        BookingRequest request = new BookingRequest(1L, 10L);

        Booking booking = mapper.toEntity(request, user, schedule);

        assertThat(booking.getUser()).isEqualTo(user);
        assertThat(booking.getSchedule()).isEqualTo(schedule);
        assertThat(booking.getId()).isNull();
        assertThat(booking.getPnr()).isNull();
        assertThat(booking.getBookingTime()).isNull();
        assertThat(booking.getStatus()).isNull();
        assertThat(booking.getTotalAmount()).isNull();
        assertThat(booking.getPromoCode()).isNull();
    }

    @Test
    void toResponse_flattensUserScheduleAndPromoCodeIds() {
        User user = User.builder().id(1L).build();
        Schedule schedule = Schedule.builder().id(10L).build();
        PromoCode promoCode = PromoCode.builder().code("SAVE10").discountType(DiscountType.PERCENTAGE).build();
        Booking booking = Booking.builder()
                .id(500L)
                .user(user)
                .schedule(schedule)
                .pnr("ABC234")
                .bookingTime(Instant.parse("2026-08-01T00:00:00Z"))
                .status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("50.00"))
                .promoCode(promoCode)
                .build();

        BookingResponse response = mapper.toResponse(booking);

        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.scheduleId()).isEqualTo(10L);
        assertThat(response.pnr()).isEqualTo("ABC234");
        assertThat(response.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(response.totalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.promoCode()).isEqualTo("SAVE10");
    }

    @Test
    void toResponse_whenNoPromoCodeApplied_leavesPromoCodeNull() {
        Booking booking = Booking.builder()
                .id(500L)
                .user(User.builder().id(1L).build())
                .schedule(Schedule.builder().id(10L).build())
                .totalAmount(BigDecimal.TEN)
                .build();

        BookingResponse response = mapper.toResponse(booking);

        assertThat(response.promoCode()).isNull();
    }

    @Test
    void toEntity_whenEveryArgumentIsNull_returnsNull() {
        assertThat(mapper.toEntity(null, null, null)).isNull();
    }

    @Test
    void toEntity_whenOnlyUserIsNonNull_stillBuildsABooking() {
        User user = User.builder().id(1L).build();

        Booking booking = mapper.toEntity(null, user, null);

        assertThat(booking.getUser()).isEqualTo(user);
    }

    @Test
    void toEntity_whenOnlyScheduleIsNonNull_stillBuildsABooking() {
        Schedule schedule = Schedule.builder().id(10L).build();

        Booking booking = mapper.toEntity(null, null, schedule);

        assertThat(booking.getSchedule()).isEqualTo(schedule);
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenUserAndScheduleAreNull_leavesFlattenedIdsNull() {
        Booking booking = Booking.builder().id(500L).build();

        BookingResponse response = mapper.toResponse(booking);

        assertThat(response.userId()).isNull();
        assertThat(response.scheduleId()).isNull();
    }
}
