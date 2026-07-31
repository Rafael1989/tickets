package com.ticketwave.booking.controller;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.RescheduleRequest;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.service.PaymentService;
import com.ticketwave.payment.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Requires a bearer JWT (see Authentication). Not rate-limited.")
public class BookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final RefundService refundService;

    public BookingController(BookingService bookingService, PaymentService paymentService, RefundService refundService) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.refundService = refundService;
    }

    @PostMapping
    @Operation(summary = "Create a booking and hold its seats")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Booking created, in INITIATED status"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "User, schedule, or passenger not found"),
            @ApiResponse(responseCode = "409", description = "A selected seat is unavailable")
    })
    public ResponseEntity<BookingDetailResponse> createBooking(
            Authentication authentication,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.createBooking(authentication.getName(), request));
    }

    /**
     * Records the payment and, on success, confirms the booking as a
     * result — there is no separate manual "confirm" step in this API,
     * since a booking should never be confirmable without having paid.
     */
    @PostMapping("/{id}/payments")
    @Operation(
            summary = "Record a payment, confirming the booking on success",
            description = "Idempotent on the request's reference: replaying the same reference returns the original payment instead of double-charging or re-confirming."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Payment recorded (or the original result, if this reference was already used)"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking"),
            @ApiResponse(responseCode = "409", description = "Booking isn't INITIATED, or the amount doesn't match its total")
    })
    public ResponseEntity<PaymentResponse> recordPayment(
            @PathVariable("id") Long bookingId,
            @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.recordPayment(bookingId, request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a booking's details")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking")
    })
    public ResponseEntity<BookingDetailResponse> getBooking(@PathVariable("id") Long bookingId) {
        return ResponseEntity.ok(bookingService.getBooking(bookingId));
    }

    @GetMapping("/pnr/{pnr}")
    @Operation(summary = "Look up a booking by PNR (support/admin)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not support or admin"),
            @ApiResponse(responseCode = "404", description = "No such PNR")
    })
    public ResponseEntity<BookingDetailResponse> getBookingByPnr(@PathVariable("pnr") String pnr) {
        return ResponseEntity.ok(bookingService.getBookingByPnr(pnr));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(
            summary = "Change an unpaid booking's schedule/seats",
            description = "Only available while the booking is still INITIATED (unpaid). Releases the old seat holds, holds the new seats, and recalculates the total against the new schedule."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking rescheduled"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking, schedule, or passenger"),
            @ApiResponse(responseCode = "409", description = "Booking isn't INITIATED, or a selected seat is unavailable")
    })
    public ResponseEntity<BookingDetailResponse> rescheduleBooking(
            @PathVariable("id") Long bookingId,
            @Valid @RequestBody RescheduleRequest request
    ) {
        return ResponseEntity.ok(bookingService.rescheduleBooking(bookingId, request));
    }

    @PostMapping("/{id}/refunds")
    @Operation(
            summary = "Initiate a refund for a paid booking",
            description = "Applies the cancellation policy (full refund, prorated partial refund, or blocked if departure is too close), creates a PENDING refund, and cancels the booking. Settling the refund as PROCESSED/REJECTED is a separate support/admin action — see PUT /api/refunds/{id}/process."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Refund initiated, in PENDING status; booking cancelled"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking, or no successful payment on record"),
            @ApiResponse(responseCode = "409", description = "Booking isn't CONFIRMED, or departure is too imminent to cancel")
    })
    public ResponseEntity<RefundResponse> initiateRefund(@PathVariable("id") Long bookingId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(refundService.initiateRefund(bookingId));
    }
}
