package com.ticketwave.booking.entity;

import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Column(name = "pnr", nullable = false, unique = true, length = 10)
    private String pnr;

    @Column(name = "booking_time", nullable = false, updatable = false)
    private Instant bookingTime;

    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id")
    private PromoCode promoCode;

    /**
     * Optional caller-supplied idempotency key: a retried POST /api/bookings
     * carrying the same key is rejected as a duplicate (see
     * BookingServiceImpl#createBooking) rather than creating a second
     * booking and double-holding seats. Null for callers that don't send
     * one, which the unique constraint allows any number of.
     */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    /**
     * Optimistic lock: two concurrent requests against the same booking (e.g.
     * two refund initiations racing each other) can otherwise both read
     * CONFIRMED before either commits its status change, producing duplicate
     * refunds. A stale write now fails fast (ObjectOptimisticLockingFailureException,
     * mapped to 409 by GlobalExceptionHandler) instead of silently overwriting
     * the other request's change.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        if (bookingTime == null) {
            bookingTime = Instant.now();
        }
    }
}
