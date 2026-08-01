package com.ticketwave.booking.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.RescheduleRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingItem;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.BookingNotFoundException;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.mapper.BookingItemMapper;
import com.ticketwave.booking.mapper.BookingMapper;
import com.ticketwave.booking.repository.BookingItemRepository;
import com.ticketwave.booking.repository.BookingRepository;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.service.SeatHoldService;
import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.service.PricingService;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.PassengerNotFoundException;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.PassengerRepository;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final PassengerRepository passengerRepository;
    private final SeatHoldService seatHoldService;
    private final PnrGenerator pnrGenerator;
    private final PricingService pricingService;
    private final BookingMapper bookingMapper;
    private final BookingItemMapper bookingItemMapper;

    public BookingServiceImpl(
            BookingRepository bookingRepository,
            BookingItemRepository bookingItemRepository,
            UserRepository userRepository,
            ScheduleRepository scheduleRepository,
            PassengerRepository passengerRepository,
            SeatHoldService seatHoldService,
            PnrGenerator pnrGenerator,
            PricingService pricingService,
            BookingMapper bookingMapper,
            BookingItemMapper bookingItemMapper
    ) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.passengerRepository = passengerRepository;
        this.seatHoldService = seatHoldService;
        this.pnrGenerator = pnrGenerator;
        this.pricingService = pricingService;
        this.bookingMapper = bookingMapper;
        this.bookingItemMapper = bookingItemMapper;
    }

    @Override
    @Transactional
    public BookingDetailResponse createBooking(String username, CreateBookingRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        Schedule schedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new ScheduleNotFoundException(request.scheduleId()));

        Booking booking = bookingRepository.save(Booking.builder()
                .user(user)
                .schedule(schedule)
                .pnr(pnrGenerator.generate())
                .status(BookingStatus.INITIATED)
                .totalAmount(BigDecimal.ZERO)
                .build());

        // Seats are locked in a fixed (ascending id) order so that two
        // bookings racing over overlapping seat sets can never deadlock on
        // each other's pessimistic locks.
        List<SeatSelection> orderedSelections = request.seatSelections().stream()
                .sorted(Comparator.comparing(SeatSelection::seatId))
                .toList();

        List<BookingItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (SeatSelection selection : orderedSelections) {
            Passenger passenger = passengerRepository.findById(selection.passengerId())
                    .filter(candidate -> candidate.getUser().getId().equals(user.getId()))
                    .orElseThrow(() -> new PassengerNotFoundException(selection.passengerId()));

            Seat seat = seatHoldService.holdSeat(selection.seatId(), user);
            BigDecimal fare = pricingService.calculateSeatFare(schedule, seat);

            items.add(bookingItemRepository.save(BookingItem.builder()
                    .booking(booking)
                    .seat(seat)
                    .passenger(passenger)
                    .fare(fare)
                    .build()));
            subtotal = subtotal.add(fare);
        }

        BigDecimal totalAmount = subtotal;
        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            PromoCodeApplication application = pricingService.applyPromoCode(request.promoCode(), subtotal);
            booking.setPromoCode(application.promoCode());
            totalAmount = subtotal.subtract(application.discountAmount());
        }
        booking.setTotalAmount(totalAmount);

        return toDetailResponse(booking, items);
    }

    /**
     * INITIATED (first attempt), FAILED (retry after a decline), or already
     * PAYMENT_PROCESSING (idempotent no-op - a concurrent request racing the
     * same in-flight attempt, e.g. PaymentServiceImpl's own same-reference
     * retry, re-affirms rather than errors) -> PAYMENT_PROCESSING. Rejects
     * CONFIRMED and CANCELLED outright, which is what actually stops a stray
     * payment attempt from reopening a booking that's already been settled.
     */
    @Override
    @Transactional
    public BookingDetailResponse markPaymentProcessing(Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking,
                EnumSet.of(BookingStatus.INITIATED, BookingStatus.FAILED, BookingStatus.PAYMENT_PROCESSING),
                BookingStatus.PAYMENT_PROCESSING);

        booking.setStatus(BookingStatus.PAYMENT_PROCESSING);
        List<BookingItem> items = bookingItemRepository.findByBookingId(bookingId);
        return toDetailResponse(booking, items);
    }

    @Override
    @Transactional
    public BookingDetailResponse confirmBooking(Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.PAYMENT_PROCESSING, BookingStatus.CONFIRMED);

        List<BookingItem> items = bookingItemRepository.findByBookingId(bookingId);
        for (BookingItem item : items) {
            seatHoldService.confirmHold(item.getSeat().getId());
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        return toDetailResponse(booking, items);
    }

    /**
     * PAYMENT_PROCESSING -> FAILED. Deliberately does not release the seat
     * holds (unlike cancelBooking) - a decline should let the customer retry
     * the same seats, not lose them.
     */
    @Override
    @Transactional
    public BookingDetailResponse failBooking(Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.PAYMENT_PROCESSING, BookingStatus.FAILED);

        booking.setStatus(BookingStatus.FAILED);
        List<BookingItem> items = bookingItemRepository.findByBookingId(bookingId);
        return toDetailResponse(booking, items);
    }

    /**
     * Accepts INITIATED (pre-payment abandonment), CONFIRMED (post-payment),
     * and FAILED (customer walking away after a decline) bookings - anything
     * except CANCELLED itself or PAYMENT_PROCESSING, since a payment attempt
     * actively in flight shouldn't be cancelled out from under it. Whether
     * cancelling a CONFIRMED booking also needs a refund is the refund
     * flow's decision (policy checks, proration), made before it ever calls
     * this method — this method only knows how to free the seats and flip
     * the status.
     */
    @Override
    @Transactional
    public BookingDetailResponse cancelBooking(Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        if (booking.getStatus() == BookingStatus.CANCELLED || booking.getStatus() == BookingStatus.PAYMENT_PROCESSING) {
            throw new InvalidBookingStateException(booking.getId(), booking.getStatus(), BookingStatus.CANCELLED);
        }

        List<BookingItem> items = bookingItemRepository.findByBookingId(bookingId);
        for (BookingItem item : items) {
            seatHoldService.releaseSeat(item.getSeat().getId());
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return toDetailResponse(booking, items);
    }

    /**
     * Only usable while INITIATED (unpaid) — see RescheduleRequest's javadoc
     * for why a CONFIRMED (paid) booking isn't handled here.
     */
    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    @Transactional
    public BookingDetailResponse rescheduleBooking(Long bookingId, RescheduleRequest request) {
        Booking booking = getBookingOrThrow(bookingId);
        requireStatus(booking, BookingStatus.INITIATED, BookingStatus.INITIATED);

        Schedule newSchedule = scheduleRepository.findById(request.scheduleId())
                .orElseThrow(() -> new ScheduleNotFoundException(request.scheduleId()));

        List<BookingItem> oldItems = bookingItemRepository.findByBookingId(bookingId);
        for (BookingItem item : oldItems) {
            seatHoldService.releaseSeat(item.getSeat().getId());
        }
        bookingItemRepository.deleteAll(oldItems);

        booking.setSchedule(newSchedule);

        List<SeatSelection> orderedSelections = request.seatSelections().stream()
                .sorted(Comparator.comparing(SeatSelection::seatId))
                .toList();

        List<BookingItem> newItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (SeatSelection selection : orderedSelections) {
            Passenger passenger = passengerRepository.findById(selection.passengerId())
                    .filter(candidate -> candidate.getUser().getId().equals(booking.getUser().getId()))
                    .orElseThrow(() -> new PassengerNotFoundException(selection.passengerId()));

            Seat seat = seatHoldService.holdSeat(selection.seatId(), booking.getUser());
            BigDecimal fare = pricingService.calculateSeatFare(newSchedule, seat);

            newItems.add(bookingItemRepository.save(BookingItem.builder()
                    .booking(booking)
                    .seat(seat)
                    .passenger(passenger)
                    .fare(fare)
                    .build()));
            subtotal = subtotal.add(fare);
        }

        BigDecimal totalAmount = subtotal;
        if (booking.getPromoCode() != null) {
            PromoCodeApplication application = pricingService.applyPromoCode(booking.getPromoCode().getCode(), subtotal);
            totalAmount = subtotal.subtract(application.discountAmount());
        }
        booking.setTotalAmount(totalAmount);

        return toDetailResponse(booking, newItems);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN') or @bookingOwnership.isOwnedBy(#bookingId, authentication.name)")
    @Transactional(readOnly = true)
    public BookingDetailResponse getBooking(Long bookingId) {
        Booking booking = getBookingOrThrow(bookingId);
        List<BookingItem> items = bookingItemRepository.findByBookingId(bookingId);
        return toDetailResponse(booking, items);
    }

    @Override
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingByPnr(String pnr) {
        Booking booking = bookingRepository.findByPnr(pnr)
                .orElseThrow(() -> new BookingNotFoundException(pnr));
        List<BookingItem> items = bookingItemRepository.findByBookingId(booking.getId());
        return toDetailResponse(booking, items);
    }

    private Booking getBookingOrThrow(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private void requireStatus(Booking booking, BookingStatus required, BookingStatus attempted) {
        requireStatus(booking, EnumSet.of(required), attempted);
    }

    private void requireStatus(Booking booking, Set<BookingStatus> allowed, BookingStatus attempted) {
        if (!allowed.contains(booking.getStatus())) {
            throw new InvalidBookingStateException(booking.getId(), booking.getStatus(), attempted);
        }
    }

    private BookingDetailResponse toDetailResponse(Booking booking, List<BookingItem> items) {
        return new BookingDetailResponse(
                bookingMapper.toResponse(booking),
                items.stream().map(bookingItemMapper::toResponse).toList());
    }
}
