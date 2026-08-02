package com.ticketwave.booking.controller;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.BookingSearchResult;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.RescheduleQuoteResponse;
import com.ticketwave.booking.dto.RescheduleRequest;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.dto.RefundQuoteResponse;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.dto.ThreeDsConfirmationRequest;
import com.ticketwave.payment.service.PaymentService;
import com.ticketwave.payment.service.RefundService;
import com.ticketwave.payment.service.RescheduleService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Requires a bearer JWT (see Authentication). Not rate-limited.")
public class BookingController {

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final RescheduleService rescheduleService;

    public BookingController(
            BookingService bookingService,
            PaymentService paymentService,
            RefundService refundService,
            RescheduleService rescheduleService
    ) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.refundService = refundService;
        this.rescheduleService = rescheduleService;
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

    @PutMapping("/{id}/confirm")
    @Operation(
            summary = "Confirm a booking (compatibility no-op)",
            description = "This API only ever confirms a booking as a result of a successful payment — see POST .../payments and POST .../payments/{paymentId}/confirm-3ds; a booking can never be confirmed without having paid. This endpoint exists only for callers expecting an explicit confirm step: it succeeds as a no-op if the booking is already CONFIRMED, and fails with 409 otherwise. It never changes booking state."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking is CONFIRMED"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking"),
            @ApiResponse(responseCode = "409", description = "Booking isn't CONFIRMED yet — confirm it via POST .../payments instead")
    })
    public ResponseEntity<BookingDetailResponse> confirmBooking(@PathVariable("id") Long bookingId) {
        return ResponseEntity.ok(bookingService.requireConfirmed(bookingId));
    }

    /**
     * Records the payment and, on success, confirms the booking as a
     * result — there is no separate state-changing "confirm" step in this
     * API, since a booking should never be confirmable without having paid.
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

    @PostMapping("/{id}/payments/{paymentId}/confirm-3ds")
    @Operation(
            summary = "Settle a PENDING_3DS payment's simulated challenge",
            description = "code must match the simulated authentication code; a mismatch fails the payment/booking exactly like an ordinary decline. See POST .../payments — a card requiring 3DS lands here instead of being immediately approved/declined."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Challenge settled (payment succeeded or failed)"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking or payment"),
            @ApiResponse(responseCode = "409", description = "Payment isn't currently PENDING_3DS")
    })
    public ResponseEntity<PaymentResponse> confirmThreeDs(
            @PathVariable("id") Long bookingId,
            @PathVariable("paymentId") Long paymentId,
            @Valid @RequestBody ThreeDsConfirmationRequest request
    ) {
        return ResponseEntity.ok(paymentService.confirmThreeDs(bookingId, paymentId, request.code()));
    }

    @GetMapping("/me")
    @Operation(
            summary = "List the authenticated customer's own bookings",
            description = "Every booking regardless of status (INITIATED/CONFIRMED/CANCELLED/etc.), newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zero or more bookings"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
    })
    public ResponseEntity<List<BookingSearchResult>> listMyBookings(Authentication authentication) {
        return ResponseEntity.ok(bookingService.listMyBookings(authentication.getName()));
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

    @GetMapping("/search")
    @Operation(
            summary = "Search bookings by PNR, customer email, or passenger name (support/admin)",
            description = "Matches an exact PNR, or a case-insensitive substring of the customer's email or a passenger's full name. Returns at most 25 matches, newest first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zero or more matching bookings"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Caller is not support or admin")
    })
    public ResponseEntity<List<BookingSearchResult>> searchBookings(@RequestParam("query") String query) {
        return ResponseEntity.ok(bookingService.searchBookings(query));
    }

    @GetMapping("/pnr/{pnr}/lookup")
    @Operation(
            summary = "Guest \"find my booking\" lookup by PNR and email",
            description = "Public — no bearer token needed. email must match the booking's account email as a second factor, so a bare PNR guess can't retrieve someone else's itinerary.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking found"),
            @ApiResponse(responseCode = "404", description = "No such PNR, or email doesn't match it"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<BookingDetailResponse> lookupByPnrAndEmail(
            @PathVariable("pnr") String pnr,
            @RequestParam("email") String email
    ) {
        return ResponseEntity.ok(bookingService.lookupByPnrAndEmail(pnr, email));
    }

    @GetMapping("/{id}/reschedule-quote")
    @Operation(
            summary = "Preview the fare-difference outcome of rescheduling to a new schedule/seats",
            description = "Read-only. For an INITIATED booking, always eligible and free. For a CONFIRMED booking, applies the same departure-proximity window as a cancellation, and reports whether the fare difference requires collecting a payment."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote computed (eligible may be false if departure is too imminent)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking, schedule, or seat"),
            @ApiResponse(responseCode = "409", description = "Booking isn't INITIATED or CONFIRMED")
    })
    public ResponseEntity<RescheduleQuoteResponse> previewReschedule(
            @PathVariable("id") Long bookingId,
            @RequestParam("scheduleId") Long scheduleId,
            @RequestParam("seatIds") List<Long> seatIds
    ) {
        return ResponseEntity.ok(rescheduleService.previewReschedule(bookingId, scheduleId, seatIds));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(
            summary = "Change a booking's schedule/seats",
            description = "For an INITIATED (unpaid) booking, a free change. For a CONFIRMED (paid) booking, applies the same departure-proximity window as a cancellation, then settles the fare difference: collects a top-up payment (paymentMethod/paymentReference/cardNumber) for an upgrade, or issues a RESCHEDULE_CREDIT refund for a downgrade. See GET /{id}/reschedule-quote to preview the outcome first."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Booking rescheduled"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking, schedule, or passenger"),
            @ApiResponse(responseCode = "409", description = "Booking isn't INITIATED or CONFIRMED, a selected seat is unavailable, departure is too imminent, or (for an upgrade) payment was missing/declined")
    })
    public ResponseEntity<BookingDetailResponse> rescheduleBooking(
            @PathVariable("id") Long bookingId,
            @Valid @RequestBody RescheduleRequest request
    ) {
        return ResponseEntity.ok(rescheduleService.reschedule(bookingId, request));
    }

    @GetMapping("/{id}/refund-quote")
    @Operation(
            summary = "Preview the cancellation policy outcome for a CONFIRMED booking",
            description = "Read-only: computes the same eligibility window and prorated amount POST /{id}/refunds would apply, without cancelling the booking or creating a Refund. Used to show a customer the refund breakdown before they commit."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote computed (eligible may be false if departure is too imminent)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking, or no successful payment on record"),
            @ApiResponse(responseCode = "409", description = "Booking isn't CONFIRMED")
    })
    public ResponseEntity<RefundQuoteResponse> previewRefund(@PathVariable("id") Long bookingId) {
        return ResponseEntity.ok(refundService.previewRefund(bookingId));
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

    @GetMapping("/{id}/refunds")
    @Operation(
            summary = "List every refund raised against a booking's payment(s)",
            description = "Newest first. Typically zero or one, but a booking rescheduled downward can also carry a RESCHEDULE_CREDIT refund. Lets support find a PENDING refund to settle without already knowing its id."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Zero or more refunds"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "403", description = "Booking belongs to a different customer"),
            @ApiResponse(responseCode = "404", description = "No such booking")
    })
    public ResponseEntity<List<RefundResponse>> listRefunds(@PathVariable("id") Long bookingId) {
        return ResponseEntity.ok(refundService.listRefundsForBooking(bookingId));
    }
}
