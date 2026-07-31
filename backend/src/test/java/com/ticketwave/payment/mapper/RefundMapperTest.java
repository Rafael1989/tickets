package com.ticketwave.payment.mapper;

import com.ticketwave.payment.dto.RefundRequest;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.entity.Refund;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.user.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefundMapperTest {

    private final RefundMapper mapper = new RefundMapperImpl();

    @Test
    void toEntity_mapsTheDisambiguatedAmountAndIgnoresServerControlledFields() {
        // Regression check for the ambiguity MapStruct flagged at compile
        // time in P4: both RefundRequest.amount() and Payment.amount() exist,
        // and the mapper must take the request's, not the payment's.
        Payment payment = Payment.builder().id(1L).amount(new BigDecimal("999.99")).build();
        RefundRequest request = new RefundRequest(1L, new BigDecimal("50.00"), "FULL_REFUND");

        Refund refund = mapper.toEntity(request, payment);

        assertThat(refund.getPayment()).isEqualTo(payment);
        assertThat(refund.getAmount()).isEqualByComparingTo("50.00");
        assertThat(refund.getPolicyCode()).isEqualTo("FULL_REFUND");
        assertThat(refund.getId()).isNull();
        assertThat(refund.getStatus()).isNull();
        assertThat(refund.getProcessedBy()).isNull();
        assertThat(refund.getProcessedAt()).isNull();
    }

    @Test
    void toResponse_whenProcessed_flattensPaymentAndProcessedByIds() {
        Refund refund = Refund.builder()
                .id(1L)
                .payment(Payment.builder().id(2L).status(PaymentStatus.REFUNDED).build())
                .amount(new BigDecimal("50.00"))
                .policyCode("FULL_REFUND")
                .status(RefundStatus.PROCESSED)
                .processedBy(User.builder().id(9L).build())
                .processedAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();

        RefundResponse response = mapper.toResponse(refund);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.paymentId()).isEqualTo(2L);
        assertThat(response.processedByUserId()).isEqualTo(9L);
        assertThat(response.status()).isEqualTo(RefundStatus.PROCESSED);
        assertThat(response.processedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }

    @Test
    void toResponse_whenStillPending_leavesProcessedByIdNull() {
        Refund refund = Refund.builder()
                .id(1L)
                .payment(Payment.builder().id(2L).build())
                .amount(new BigDecimal("50.00"))
                .policyCode("FULL_REFUND")
                .status(RefundStatus.PENDING)
                .build();

        RefundResponse response = mapper.toResponse(refund);

        assertThat(response.processedByUserId()).isNull();
        assertThat(response.processedAt()).isNull();
    }

    @Test
    void toEntity_whenBothArgumentsAreNull_returnsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void toEntity_whenRequestIsNull_stillSetsPaymentOnly() {
        Payment payment = Payment.builder().id(1L).build();

        Refund refund = mapper.toEntity(null, payment);

        assertThat(refund.getPayment()).isEqualTo(payment);
        assertThat(refund.getAmount()).isNull();
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenPaymentIsNull_leavesPaymentIdNull() {
        Refund refund = Refund.builder().id(1L).build();

        RefundResponse response = mapper.toResponse(refund);

        assertThat(response.paymentId()).isNull();
    }
}
