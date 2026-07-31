package com.ticketwave.booking.repository;

import com.ticketwave.booking.entity.BookingItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingItemRepository extends JpaRepository<BookingItem, Long> {

    List<BookingItem> findByBookingId(Long bookingId);

    List<BookingItem> findBySeatId(Long seatId);
}
