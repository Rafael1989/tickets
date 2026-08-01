package com.ticketwave.payment.mapper;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapperImpl();

    @Test
    void toEntity_setsBookingAndAmountMethodReference_ignoresServerControlledFields() {
        Booking booking = Booking.builder().id(500L).build();
        PaymentRequest request = new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", "4242424242424242");

        Payment payment = mapper.toEntity(request, booking);

        assertThat(payment.getBooking()).isEqualTo(booking);
        assertThat(payment.getAmount()).isEqualByComparingTo("50.00");
        assertThat(payment.getMethod()).isEqualTo("card");
        assertThat(payment.getReference()).isEqualTo("REF-1");
        assertThat(payment.getId()).isNull();
        assertThat(payment.getStatus()).isNull();
        assertThat(payment.getPaidAt()).isNull();
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void toEntity_whenBothArgumentsAreNull_returnsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void toEntity_whenRequestIsNull_stillSetsBookingOnly() {
        Booking booking = Booking.builder().id(500L).build();

        Payment payment = mapper.toEntity(null, booking);

        assertThat(payment.getBooking()).isEqualTo(booking);
        assertThat(payment.getAmount()).isNull();
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenBookingIsNull_leavesBookingIdNull() {
        Payment payment = Payment.builder().id(1L).build();

        PaymentResponse response = mapper.toResponse(payment);

        assertThat(response.bookingId()).isNull();
    }

    @Test
    void toResponse_flattensBookingId() {
        Payment payment = Payment.builder()
                .id(1L)
                .booking(Booking.builder().id(500L).build())
                .amount(new BigDecimal("50.00"))
                .method("card")
                .reference("REF-1")
                .status(PaymentStatus.SUCCEEDED)
                .paidAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();

        PaymentResponse response = mapper.toResponse(payment);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.bookingId()).isEqualTo(500L);
        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.method()).isEqualTo("card");
        assertThat(response.reference()).isEqualTo("REF-1");
        assertThat(response.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.paidAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        assertThat(response.failureReason()).isNull();
    }

    @Test
    void toResponse_forFailedPayment_carriesFailureReason() {
        Payment payment = Payment.builder()
                .id(1L)
                .booking(Booking.builder().id(500L).build())
                .amount(new BigDecimal("50.00"))
                .method("card")
                .reference("REF-1")
                .status(PaymentStatus.FAILED)
                .failureReason("Your card was declined.")
                .build();

        PaymentResponse response = mapper.toResponse(payment);

        assertThat(response.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.failureReason()).isEqualTo("Your card was declined.");
    }
}
