package com.ticketwave.payment.service;

import com.ticketwave.payment.dto.RefundDecision;
import com.ticketwave.payment.dto.RefundQuoteResponse;
import com.ticketwave.payment.dto.RefundResponse;

import java.math.BigDecimal;
import java.util.List;

public interface RefundService {

    /**
     * Every refund ever raised against this booking's payment(s), newest
     * first — typically zero or one, but a booking that was later
     * rescheduled downward can also carry a RESCHEDULE_CREDIT refund. Lets a
     * support agent find the PENDING refund to settle without already
     * knowing its id from elsewhere (e.g. an audit log entry).
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     */
    List<RefundResponse> listRefundsForBooking(Long bookingId);

    /**
     * Read-only preview of the cancellation policy outcome for a CONFIRMED
     * booking - same eligibility window and proration math as
     * initiateRefund, but never cancels the booking or writes a Refund row.
     * Unlike initiateRefund, an imminent departure doesn't throw here: it's
     * reported back as {@code eligible = false} so a UI can render "not
     * eligible" instead of having to catch an exception just to render a
     * preview.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently CONFIRMED
     * @throws com.ticketwave.payment.exception.PaymentNotFoundException if the booking has no successful payment on record
     */
    RefundQuoteResponse previewRefund(Long bookingId);

    /**
     * Applies the cancellation policy to a CONFIRMED booking's schedule,
     * creates a PENDING refund for the prorated amount against its
     * successful payment, and cancels the booking (releasing its seats).
     * The refund itself is not settled here — see processRefund.
     *
     * @throws com.ticketwave.booking.exception.BookingNotFoundException if no such booking exists
     * @throws com.ticketwave.booking.exception.InvalidBookingStateException if the booking isn't currently CONFIRMED
     * @throws com.ticketwave.payment.exception.PaymentNotFoundException if the booking has no successful payment on record
     * @throws com.ticketwave.payment.exception.CancellationNotAllowedException if departure is too imminent to cancel
     */
    RefundResponse initiateRefund(Long bookingId);

    /**
     * Support/admin action settling a PENDING refund as PROCESSED or
     * REJECTED. On approval, the underlying payment is marked REFUNDED.
     * processedByUsername is the authenticated caller, resolved server-side
     * — never a client-supplied id, the same ownership discipline as every
     * other identity-bearing call in this app.
     * <p>
     * overrideAmount/overrideReason let the agent waive part or all of the
     * policy-computed cancellation fee on approval — both are ignored on a
     * REJECT decision, where there is no fee to waive. When overrideAmount
     * is supplied, overrideReason is mandatory and the amount must not
     * exceed the underlying payment's amount.
     *
     * @throws com.ticketwave.payment.exception.RefundNotFoundException if no such refund exists
     * @throws com.ticketwave.payment.exception.InvalidRefundStateException if the refund isn't currently PENDING
     * @throws com.ticketwave.user.exception.UserNotFoundException if processedByUsername doesn't resolve to a user
     * @throws com.ticketwave.payment.exception.RefundOverrideReasonRequiredException if overrideAmount is supplied without overrideReason
     * @throws com.ticketwave.payment.exception.RefundOverrideAmountExceedsPaymentException if overrideAmount exceeds the payment's amount
     */
    RefundResponse processRefund(
            Long refundId,
            String processedByUsername,
            RefundDecision decision,
            BigDecimal overrideAmount,
            String overrideReason
    );
}
