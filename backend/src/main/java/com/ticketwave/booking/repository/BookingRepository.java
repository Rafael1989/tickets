package com.ticketwave.booking.repository;

import com.ticketwave.booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPnr(String pnr);

    boolean existsByPnr(String pnr);

    List<Booking> findByUserId(Long userId);

    List<Booking> findByScheduleId(Long scheduleId);

    boolean existsByIdAndUserUsername(Long id, String username);
}
