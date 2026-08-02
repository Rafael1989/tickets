package com.ticketwave.booking.repository;

import com.ticketwave.booking.entity.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPnr(String pnr);

    boolean existsByPnr(String pnr);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    List<Booking> findByUserId(Long userId);

    List<Booking> findByUserIdOrderByBookingTimeDesc(Long userId);

    List<Booking> findByScheduleId(Long scheduleId);

    boolean existsByIdAndUserUsername(Long id, String username);

    /**
     * Support omni-search: an exact (case-insensitive) PNR match, or a
     * substring match against the booking's customer email or any of its
     * passengers' full names. DISTINCT because a booking with multiple
     * passengers could otherwise join-fan-out into duplicate rows.
     * likePattern is a pre-escaped, pre-lowercased "%...%" pattern (see
     * BookingServiceImpl) so a caller's literal % or _ can't widen the match.
     */
    @Query("""
            SELECT DISTINCT b FROM Booking b
            JOIN b.user u
            LEFT JOIN BookingItem bi ON bi.booking = b
            LEFT JOIN bi.passenger p
            WHERE LOWER(b.pnr) = LOWER(:query)
               OR LOWER(u.email) LIKE :likePattern ESCAPE '\\'
               OR LOWER(p.fullName) LIKE :likePattern ESCAPE '\\'
            ORDER BY b.bookingTime DESC
            """)
    Page<Booking> search(@Param("query") String query, @Param("likePattern") String likePattern, Pageable pageable);

    /**
     * One grouped COUNT+SUM per route, for the operator analytics report —
     * instead of one query per route. Only CONFIRMED bookings count as
     * "sold": INITIATED/PAYMENT_PROCESSING/FAILED never collected revenue,
     * and CANCELLED gave it back.
     */
    @Query("""
            SELECT b.schedule.route.id AS routeId, COUNT(b) AS bookingCount, COALESCE(SUM(b.totalAmount), 0) AS revenue
            FROM Booking b
            WHERE b.schedule.route.id IN :routeIds AND b.status = com.ticketwave.booking.entity.BookingStatus.CONFIRMED
            GROUP BY b.schedule.route.id
            """)
    List<RouteBookingStats> aggregateConfirmedBookingsByRouteId(@Param("routeIds") Collection<Long> routeIds);
}
