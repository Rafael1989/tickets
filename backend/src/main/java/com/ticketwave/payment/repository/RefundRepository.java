package com.ticketwave.payment.repository;

import com.ticketwave.payment.entity.Refund;
import com.ticketwave.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByPaymentBookingIdOrderByIdDesc(Long bookingId);

    /**
     * Guards against a second refund request while one is still awaiting
     * support review. The booking stays CONFIRMED until an approval, so its
     * status alone no longer blocks a repeat request the way it did when
     * initiateRefund cancelled the booking up front.
     */
    boolean existsByPaymentBookingIdAndStatus(Long bookingId, RefundStatus status);
}
