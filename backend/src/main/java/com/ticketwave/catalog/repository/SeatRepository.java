package com.ticketwave.catalog.repository;

import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByScheduleId(Long scheduleId);

    List<Seat> findByScheduleIdAndStatus(Long scheduleId, SeatStatus status);

    Optional<Seat> findByScheduleIdAndSeatNumber(Long scheduleId, String seatNumber);

    long countByScheduleIdAndStatus(Long scheduleId, SeatStatus status);

    long countByScheduleId(Long scheduleId);

    /**
     * One grouped COUNT for every schedule in scheduleIds, instead of the
     * caller running countByScheduleIdAndStatus once per schedule (the N+1
     * that used to sit behind every search result page).
     */
    @Query("""
            SELECT s.schedule.id AS scheduleId, COUNT(s) AS availableCount
            FROM Seat s
            WHERE s.schedule.id IN :scheduleIds AND s.status = :status
            GROUP BY s.schedule.id
            """)
    List<ScheduleSeatCount> countAvailableGroupedByScheduleId(
            @Param("scheduleIds") Collection<Long> scheduleIds,
            @Param("status") SeatStatus status);

    /**
     * Row-locks the seat for the duration of the caller's transaction, so two
     * concurrent hold attempts on the same seat serialize instead of racing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);

    /**
     * One grouped seat-inventory/occupancy count per route, for the operator
     * analytics report — instead of one query per route.
     */
    @Query("""
            SELECT s.schedule.route.id AS routeId, COUNT(s) AS totalSeats,
                   SUM(CASE WHEN s.status = com.ticketwave.catalog.entity.SeatStatus.BOOKED THEN 1L ELSE 0L END) AS bookedSeats
            FROM Seat s
            WHERE s.schedule.route.id IN :routeIds
            GROUP BY s.schedule.route.id
            """)
    List<RouteSeatStats> aggregateSeatsByRouteId(@Param("routeIds") Collection<Long> routeIds);

    /**
     * Bulk-reclaims every HELD seat whose hold has expired, for the
     * background sweeper. On-access reclaim (a fresh hold attempt on an
     * individually expired seat) is handled in the service layer instead.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Seat s SET s.status = com.ticketwave.catalog.entity.SeatStatus.AVAILABLE, s.heldUntil = null
            WHERE s.status = com.ticketwave.catalog.entity.SeatStatus.HELD AND s.heldUntil < :now
            """)
    int releaseExpiredHolds(@Param("now") Instant now);
}
