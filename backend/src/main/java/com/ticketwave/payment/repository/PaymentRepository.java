package com.ticketwave.payment.repository;

import com.ticketwave.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByReference(String reference);

    List<Payment> findByBookingId(Long bookingId);
}
