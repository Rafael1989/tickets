package com.ticketwave.payment.repository;

import com.ticketwave.payment.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByPaymentId(Long paymentId);

    List<Refund> findByPaymentBookingIdOrderByIdDesc(Long bookingId);
}
