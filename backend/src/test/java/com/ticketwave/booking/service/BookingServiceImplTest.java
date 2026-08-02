package com.ticketwave.booking.service;

import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.BookingItemResponse;
import com.ticketwave.booking.dto.BookingResponse;
import com.ticketwave.booking.dto.BookingSearchResult;
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
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.service.SeatHoldService;
import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.entity.DiscountType;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.pricing.service.PricingService;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.PassengerNotFoundException;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.PassengerRepository;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private PassengerRepository passengerRepository;
    @Mock
    private SeatHoldService seatHoldService;
    @Mock
    private PnrGenerator pnrGenerator;
    @Mock
    private PricingService pricingService;
    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private BookingItemMapper bookingItemMapper;

    private BookingServiceImpl bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(
                bookingRepository, bookingItemRepository, userRepository, scheduleRepository,
                passengerRepository, seatHoldService, pnrGenerator, pricingService, bookingMapper, bookingItemMapper);
    }

    private static User user(long id) {
        return User.builder().id(id).username("alice").build();
    }

    private static Schedule schedule(long id, BigDecimal baseFare) {
        return Schedule.builder().id(id).baseFare(baseFare).currency("USD").build();
    }

    private static Passenger passenger(long id, User owner) {
        return Passenger.builder().id(id).fullName("Jane Doe").user(owner).build();
    }

    private static Seat seat(long id, BigDecimal priceModifier) {
        return Seat.builder().id(id).status(SeatStatus.HELD).priceModifier(priceModifier).build();
    }

    @Test
    void createBooking_holdsSeatsInAscendingIdOrderAndSumsFares() {
        User user = user(1L);
        Schedule schedule = schedule(10L, new BigDecimal("20.00"));
        Passenger passengerA = passenger(100L, user);
        Passenger passengerB = passenger(101L, user);

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(pnrGenerator.generate()).willReturn("ABC234");

        Booking savedBooking = Booking.builder().id(500L).user(user).schedule(schedule)
                .pnr("ABC234").status(BookingStatus.INITIATED).totalAmount(BigDecimal.ZERO).build();
        given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);

        given(passengerRepository.findById(100L)).willReturn(Optional.of(passengerA));
        given(passengerRepository.findById(101L)).willReturn(Optional.of(passengerB));

        // Out-of-order on purpose: seat 5 requested before seat 2.
        Seat seat5 = seat(5L, new BigDecimal("1.000"));
        Seat seat2 = seat(2L, new BigDecimal("1.500"));
        given(seatHoldService.holdSeat(5L, user)).willReturn(seat5);
        given(seatHoldService.holdSeat(2L, user)).willReturn(seat2);
        given(pricingService.calculateSeatFare(schedule, seat2)).willReturn(new BigDecimal("30.00"));
        given(pricingService.calculateSeatFare(schedule, seat5)).willReturn(new BigDecimal("20.00"));

        given(bookingItemRepository.save(any(BookingItem.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        given(bookingMapper.toResponse(savedBooking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.INITIATED, new BigDecimal("50.00"), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willAnswer(invocation -> {
            BookingItem item = invocation.getArgument(0);
            return new BookingItemResponse(null, 500L, item.getSeat().getId(), item.getPassenger().getId(), item.getFare());
        });

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(
                new SeatSelection(5L, 100L),
                new SeatSelection(2L, 101L)), null);

        BookingDetailResponse response = bookingService.createBooking("alice", request);

        InOrder holdOrder = inOrder(seatHoldService);
        holdOrder.verify(seatHoldService).holdSeat(2L, user);
        holdOrder.verify(seatHoldService).holdSeat(5L, user);

        assertThat(savedBooking.getTotalAmount()).isEqualByComparingTo("50.00");
        assertThat(response.items()).hasSize(2);
    }

    @Test
    void createBooking_withPromoCode_appliesDiscountAndRecordsPromoCodeOnBooking() {
        User user = user(1L);
        Schedule schedule = schedule(10L, new BigDecimal("20.00"));
        Passenger passenger = passenger(100L, user);

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(pnrGenerator.generate()).willReturn("ABC234");

        Booking savedBooking = Booking.builder().id(500L).user(user).schedule(schedule)
                .pnr("ABC234").status(BookingStatus.INITIATED).totalAmount(BigDecimal.ZERO).build();
        given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);

        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));
        Seat seat = seat(5L, BigDecimal.ONE);
        given(seatHoldService.holdSeat(5L, user)).willReturn(seat);
        given(pricingService.calculateSeatFare(schedule, seat)).willReturn(new BigDecimal("20.00"));

        PromoCode promoCode = PromoCode.builder().id(1L).code("SAVE10").discountType(DiscountType.PERCENTAGE).build();
        given(pricingService.applyPromoCode("SAVE10", new BigDecimal("20.00")))
                .willReturn(new PromoCodeApplication(promoCode, new BigDecimal("2.00")));

        given(bookingItemRepository.save(any(BookingItem.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(bookingMapper.toResponse(savedBooking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.INITIATED,
                        new BigDecimal("18.00"), "SAVE10"));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willAnswer(invocation -> {
            BookingItem item = invocation.getArgument(0);
            return new BookingItemResponse(null, 500L, item.getSeat().getId(), item.getPassenger().getId(), item.getFare());
        });

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), "SAVE10");

        bookingService.createBooking("alice", request);

        assertThat(savedBooking.getPromoCode()).isEqualTo(promoCode);
        assertThat(savedBooking.getTotalAmount()).isEqualByComparingTo("18.00"); // 20.00 subtotal - 2.00 discount
    }

    @Test
    void createBooking_withBlankPromoCode_skipsPromoApplicationJustLikeNull() {
        User user = user(1L);
        Schedule schedule = schedule(10L, new BigDecimal("20.00"));
        Passenger passenger = passenger(100L, user);

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(pnrGenerator.generate()).willReturn("ABC234");

        Booking savedBooking = Booking.builder().id(500L).user(user).schedule(schedule)
                .pnr("ABC234").status(BookingStatus.INITIATED).totalAmount(BigDecimal.ZERO).build();
        given(bookingRepository.save(any(Booking.class))).willReturn(savedBooking);

        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));
        Seat seat = seat(5L, BigDecimal.ONE);
        given(seatHoldService.holdSeat(5L, user)).willReturn(seat);
        given(pricingService.calculateSeatFare(schedule, seat)).willReturn(new BigDecimal("20.00"));

        given(bookingItemRepository.save(any(BookingItem.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(bookingMapper.toResponse(savedBooking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.INITIATED,
                        new BigDecimal("20.00"), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willAnswer(invocation -> {
            BookingItem item = invocation.getArgument(0);
            return new BookingItemResponse(null, 500L, item.getSeat().getId(), item.getPassenger().getId(), item.getFare());
        });

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), "   ");

        bookingService.createBooking("alice", request);

        verify(pricingService, never()).applyPromoCode(anyString(), any(BigDecimal.class));
        assertThat(savedBooking.getPromoCode()).isNull();
        assertThat(savedBooking.getTotalAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void createBooking_whenUserMissing_throwsUserNotFoundExceptionBeforeHoldingSeats() {
        given(userRepository.findByUsername("alice")).willReturn(Optional.empty());

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null);

        assertThatThrownBy(() -> bookingService.createBooking("alice", request))
                .isInstanceOf(UserNotFoundException.class);

        verify(seatHoldService, never()).holdSeat(anyLong(), any());
    }

    @Test
    void createBooking_whenScheduleMissing_throwsScheduleNotFoundException() {
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user(1L)));
        given(scheduleRepository.findById(10L)).willReturn(Optional.empty());

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null);

        assertThatThrownBy(() -> bookingService.createBooking("alice", request))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void createBooking_whenPassengerMissing_throwsPassengerNotFoundException() {
        User user = user(1L);
        Schedule schedule = schedule(10L, new BigDecimal("20.00"));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(pnrGenerator.generate()).willReturn("ABC234");
        given(bookingRepository.save(any(Booking.class))).willReturn(
                Booking.builder().id(500L).user(user).schedule(schedule).pnr("ABC234")
                        .status(BookingStatus.INITIATED).totalAmount(BigDecimal.ZERO).build());
        given(passengerRepository.findById(100L)).willReturn(Optional.empty());

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null);

        assertThatThrownBy(() -> bookingService.createBooking("alice", request))
                .isInstanceOf(PassengerNotFoundException.class);
    }

    @Test
    void createBooking_whenPassengerBelongsToAnotherUser_throwsPassengerNotFoundException() {
        User user = user(1L);
        User otherUser = User.builder().id(2L).username("mallory").build();
        Schedule schedule = schedule(10L, new BigDecimal("20.00"));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(pnrGenerator.generate()).willReturn("ABC234");
        given(bookingRepository.save(any(Booking.class))).willReturn(
                Booking.builder().id(500L).user(user).schedule(schedule).pnr("ABC234")
                        .status(BookingStatus.INITIATED).totalAmount(BigDecimal.ZERO).build());
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger(100L, otherUser)));

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null);

        assertThatThrownBy(() -> bookingService.createBooking("alice", request))
                .isInstanceOf(PassengerNotFoundException.class);

        verify(seatHoldService, never()).holdSeat(anyLong(), any());
    }

    @Test
    void createBooking_whenSeatUnavailable_propagatesSeatUnavailableException() {
        User user = user(1L);
        Schedule schedule = schedule(10L, new BigDecimal("20.00"));
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(scheduleRepository.findById(10L)).willReturn(Optional.of(schedule));
        given(pnrGenerator.generate()).willReturn("ABC234");
        given(bookingRepository.save(any(Booking.class))).willReturn(
                Booking.builder().id(500L).user(user).schedule(schedule).pnr("ABC234")
                        .status(BookingStatus.INITIATED).totalAmount(BigDecimal.ZERO).build());
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger(100L, user)));
        given(seatHoldService.holdSeat(5L, user)).willThrow(new SeatUnavailableException(5L));

        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null);

        assertThatThrownBy(() -> bookingService.createBooking("alice", request))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void confirmBooking_whenPaymentProcessing_confirmsEachSeatAndTransitionsToConfirmed() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.PAYMENT_PROCESSING)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item1 = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();
        BookingItem item2 = BookingItem.builder().id(2L).booking(booking).seat(seat(5L, BigDecimal.ONE)).build();

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item1, item2));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CONFIRMED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        bookingService.confirmBooking(500L);

        verify(seatHoldService).confirmHold(2L);
        verify(seatHoldService).confirmHold(5L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void confirmBooking_whenNotPaymentProcessing_throwsInvalidBookingStateExceptionWithoutTouchingSeats() {
        // INITIATED itself is no longer enough - a payment attempt must have
        // been marked in flight first via markPaymentProcessing.
        Booking booking = Booking.builder().id(500L).status(BookingStatus.INITIATED).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.confirmBooking(500L))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(seatHoldService, never()).confirmHold(anyLong());
    }

    @Test
    void confirmBooking_whenBookingMissing_throwsBookingNotFoundException() {
        given(bookingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.confirmBooking(999L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void markPaymentProcessing_whenInitiated_transitionsToPaymentProcessing() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.INITIATED)
                .totalAmount(new BigDecimal("50.00")).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of());
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.PAYMENT_PROCESSING, booking.getTotalAmount(), null));

        bookingService.markPaymentProcessing(500L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_PROCESSING);
    }

    @Test
    void markPaymentProcessing_whenFailed_transitionsToPaymentProcessingAsARetry() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.FAILED)
                .totalAmount(new BigDecimal("50.00")).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of());
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.PAYMENT_PROCESSING, booking.getTotalAmount(), null));

        bookingService.markPaymentProcessing(500L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_PROCESSING);
    }

    @Test
    void markPaymentProcessing_whenAlreadyConfirmed_throwsInvalidBookingStateException() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.CONFIRMED).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.markPaymentProcessing(500L))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void markPaymentProcessing_whenAlreadyPaymentProcessing_isANoOpReaffirm() {
        // A concurrent request racing the same in-flight attempt (e.g. a
        // same-reference retry) must re-affirm rather than error, or the
        // idempotent-reference recovery path in PaymentServiceImpl would
        // never get a chance to run.
        Booking booking = Booking.builder().id(500L).status(BookingStatus.PAYMENT_PROCESSING)
                .totalAmount(new BigDecimal("50.00")).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of());
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.PAYMENT_PROCESSING, booking.getTotalAmount(), null));

        bookingService.markPaymentProcessing(500L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_PROCESSING);
    }

    @Test
    void failBooking_whenPaymentProcessing_transitionsToFailedWithoutReleasingSeats() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.PAYMENT_PROCESSING)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.FAILED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        bookingService.failBooking(500L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        verify(seatHoldService, never()).releaseSeat(anyLong());
    }

    @Test
    void failBooking_whenNotPaymentProcessing_throwsInvalidBookingStateException() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.INITIATED).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.failBooking(500L))
                .isInstanceOf(InvalidBookingStateException.class);
    }

    @Test
    void cancelBooking_whenInitiated_releasesEachSeatAndTransitionsToCancelled() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.INITIATED)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item1 = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item1));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CANCELLED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        bookingService.cancelBooking(500L);

        verify(seatHoldService).releaseSeat(2L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_whenConfirmed_alsoReleasesEachSeatAndTransitionsToCancelled() {
        // A CONFIRMED (paid) booking must be cancellable too — the refund
        // flow drives this path after its own policy check passes, and
        // relies on cancelBooking to actually free the now-BOOKED seats.
        Booking booking = Booking.builder().id(500L).status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item1 = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item1));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CANCELLED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        bookingService.cancelBooking(500L);

        verify(seatHoldService).releaseSeat(2L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void cancelBooking_whenAlreadyCancelled_throwsInvalidBookingStateException() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.CANCELLED).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(500L))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(seatHoldService, never()).releaseSeat(anyLong());
    }

    @Test
    void cancelBooking_whenPaymentProcessing_throwsInvalidBookingStateExceptionWithoutTouchingSeats() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.PAYMENT_PROCESSING).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(500L))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(seatHoldService, never()).releaseSeat(anyLong());
    }

    @Test
    void cancelBooking_whenFailed_releasesEachSeatAndTransitionsToCancelled() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.FAILED)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CANCELLED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        bookingService.cancelBooking(500L);

        verify(seatHoldService).releaseSeat(2L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void getBooking_whenFound_returnsBookingWithItems() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CONFIRMED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(item)).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        BookingDetailResponse response = bookingService.getBooking(500L);

        assertThat(response.booking().id()).isEqualTo(500L);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void getBooking_whenMissing_throwsBookingNotFoundException() {
        given(bookingRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBooking(999L))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void getBookingByPnr_whenFound_returnsBookingWithItems() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();

        given(bookingRepository.findByPnr("ABC234")).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CONFIRMED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(item)).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        BookingDetailResponse response = bookingService.getBookingByPnr("ABC234");

        assertThat(response.booking().id()).isEqualTo(500L);
    }

    @Test
    void getBookingByPnr_whenMissing_throwsBookingNotFoundException() {
        given(bookingRepository.findByPnr("GHOST1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingByPnr("GHOST1"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void lookupByPnrAndEmail_whenPnrAndEmailMatch_returnsBookingWithItems() {
        User customer = User.builder().id(1L).email("alice@example.com").build();
        Booking booking = Booking.builder().id(500L).user(customer).status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("50.00")).build();
        BookingItem item = BookingItem.builder().id(1L).booking(booking).seat(seat(2L, BigDecimal.ONE)).build();

        given(bookingRepository.findByPnr("ABC234")).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(item));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CONFIRMED, booking.getTotalAmount(), null));
        given(bookingItemMapper.toResponse(item)).willReturn(
                new BookingItemResponse(1L, 500L, 2L, 100L, BigDecimal.ONE));

        BookingDetailResponse response = bookingService.lookupByPnrAndEmail("ABC234", "ALICE@EXAMPLE.COM");

        assertThat(response.booking().id()).isEqualTo(500L);
    }

    @Test
    void lookupByPnrAndEmail_whenEmailDoesNotMatch_throwsBookingNotFoundException() {
        User customer = User.builder().id(1L).email("alice@example.com").build();
        Booking booking = Booking.builder().id(500L).user(customer).build();
        given(bookingRepository.findByPnr("ABC234")).willReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.lookupByPnrAndEmail("ABC234", "mallory@example.com"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void lookupByPnrAndEmail_whenPnrMissing_throwsBookingNotFoundException() {
        given(bookingRepository.findByPnr("GHOST1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.lookupByPnrAndEmail("GHOST1", "alice@example.com"))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void searchBookings_withBlankQuery_returnsEmptyListWithoutQueryingRepository() {
        List<BookingSearchResult> results = bookingService.searchBookings("   ");

        assertThat(results).isEmpty();
        verify(bookingRepository, never()).search(any(), any(), any());
    }

    @Test
    void searchBookings_withMatches_returnsMappedResults() {
        User customer = User.builder().id(1L).username("alice").email("alice@example.com").build();
        Route route = Route.builder().id(20L).origin("NYC").destination("Boston").build();
        Schedule schedule = Schedule.builder().id(10L).route(route).departureTime(Instant.parse("2026-09-01T00:00:00Z")).build();
        Booking booking = Booking.builder().id(500L).user(customer).schedule(schedule).pnr("ABC234")
                .status(BookingStatus.CONFIRMED).totalAmount(new BigDecimal("50.00"))
                .bookingTime(Instant.parse("2026-08-01T00:00:00Z")).build();

        given(bookingRepository.search(eq("alice"), anyString(), any()))
                .willReturn(new PageImpl<>(List.of(booking)));

        List<BookingSearchResult> results = bookingService.searchBookings("alice");

        assertThat(results).hasSize(1);
        BookingSearchResult result = results.get(0);
        assertThat(result.bookingId()).isEqualTo(500L);
        assertThat(result.pnr()).isEqualTo("ABC234");
        assertThat(result.customerUsername()).isEqualTo("alice");
        assertThat(result.customerEmail()).isEqualTo("alice@example.com");
        assertThat(result.origin()).isEqualTo("NYC");
        assertThat(result.destination()).isEqualTo("Boston");
    }

    @Test
    void rescheduleBooking_whenInitiated_releasesOldSeatHoldsNewSeatAndRecalculatesTotal() {
        User user = user(1L);
        Booking booking = Booking.builder().id(500L).user(user).status(BookingStatus.INITIATED)
                .totalAmount(new BigDecimal("20.00")).build();
        Seat oldSeat = seat(2L, BigDecimal.ONE);
        BookingItem oldItem = BookingItem.builder().id(1L).booking(booking).seat(oldSeat).build();
        Schedule newSchedule = schedule(20L, new BigDecimal("30.00"));
        Passenger passenger = passenger(100L, user);
        Seat newSeat = seat(5L, new BigDecimal("1.500"));

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(oldItem));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(newSchedule));
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));
        given(seatHoldService.holdSeat(5L, user)).willReturn(newSeat);
        given(pricingService.calculateSeatFare(newSchedule, newSeat)).willReturn(new BigDecimal("45.00"));
        given(bookingItemRepository.save(any(BookingItem.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 20L, "ABC234", Instant.now(), BookingStatus.INITIATED,
                        new BigDecimal("45.00"), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willAnswer(invocation -> {
            BookingItem item = invocation.getArgument(0);
            return new BookingItemResponse(null, 500L, item.getSeat().getId(), item.getPassenger().getId(), item.getFare());
        });

        RescheduleRequest request = new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)));
        BookingDetailResponse response = bookingService.rescheduleBooking(500L, request);

        verify(seatHoldService).releaseSeat(2L);
        verify(bookingItemRepository).deleteAll(List.of(oldItem));
        assertThat(booking.getSchedule()).isEqualTo(newSchedule);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("45.00");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void rescheduleBooking_whenCancelled_throwsInvalidBookingStateException() {
        Booking booking = Booking.builder().id(500L).status(BookingStatus.CANCELLED).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));

        RescheduleRequest request = new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(500L, request))
                .isInstanceOf(InvalidBookingStateException.class);

        verify(seatHoldService, never()).releaseSeat(anyLong());
    }

    @Test
    void rescheduleBooking_whenConfirmed_isAllowedAtThisLayer_eligibilityAndBillingLiveInRescheduleService() {
        // BookingServiceImpl is just the mechanical swap now; a CONFIRMED
        // booking's departure-window eligibility and fare-difference
        // charge/credit are RescheduleService's job, applied before it ever
        // delegates here. See RescheduleServiceImplTest for that gating.
        User user = user(1L);
        Booking booking = Booking.builder().id(500L).user(user).status(BookingStatus.CONFIRMED)
                .totalAmount(new BigDecimal("20.00")).build();
        Seat oldSeat = seat(2L, BigDecimal.ONE);
        BookingItem oldItem = BookingItem.builder().id(1L).booking(booking).seat(oldSeat).build();
        Schedule newSchedule = schedule(20L, new BigDecimal("30.00"));
        Passenger passenger = passenger(100L, user);
        Seat newSeat = seat(5L, new BigDecimal("1.500"));

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of(oldItem));
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(newSchedule));
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));
        given(seatHoldService.holdSeat(5L, user)).willReturn(newSeat);
        given(pricingService.calculateSeatFare(newSchedule, newSeat)).willReturn(new BigDecimal("45.00"));
        given(bookingItemRepository.save(any(BookingItem.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 20L, "ABC234", Instant.now(), BookingStatus.CONFIRMED,
                        new BigDecimal("45.00"), null));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willAnswer(invocation -> {
            BookingItem item = invocation.getArgument(0);
            return new BookingItemResponse(null, 500L, item.getSeat().getId(), item.getPassenger().getId(), item.getFare());
        });

        RescheduleRequest request = new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)));
        BookingDetailResponse response = bookingService.rescheduleBooking(500L, request);

        assertThat(booking.getTotalAmount()).isEqualByComparingTo("45.00");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void rescheduleBooking_whenNewScheduleMissing_throwsScheduleNotFoundException() {
        Booking booking = Booking.builder().id(500L).user(user(1L)).status(BookingStatus.INITIATED).build();
        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(scheduleRepository.findById(20L)).willReturn(Optional.empty());

        RescheduleRequest request = new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(500L, request))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void rescheduleBooking_whenPassengerNotOwnedByBookingCustomer_throwsPassengerNotFoundException() {
        User owner = user(1L);
        User someoneElse = User.builder().id(2L).username("mallory").build();
        Booking booking = Booking.builder().id(500L).user(owner).status(BookingStatus.INITIATED).build();
        Schedule newSchedule = schedule(20L, new BigDecimal("30.00"));
        Passenger otherPassenger = passenger(100L, someoneElse);

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of());
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(newSchedule));
        given(passengerRepository.findById(100L)).willReturn(Optional.of(otherPassenger));

        RescheduleRequest request = new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)));

        assertThatThrownBy(() -> bookingService.rescheduleBooking(500L, request))
                .isInstanceOf(PassengerNotFoundException.class);
    }

    @Test
    void rescheduleBooking_whenPromoCodeWasApplied_reappliesDiscountToNewSubtotal() {
        User user = user(1L);
        PromoCode promoCode = PromoCode.builder().id(1L).code("SAVE10").discountType(DiscountType.PERCENTAGE).build();
        Booking booking = Booking.builder().id(500L).user(user).status(BookingStatus.INITIATED)
                .promoCode(promoCode).totalAmount(new BigDecimal("18.00")).build();
        Schedule newSchedule = schedule(20L, new BigDecimal("30.00"));
        Passenger passenger = passenger(100L, user);
        Seat newSeat = seat(5L, BigDecimal.ONE);

        given(bookingRepository.findById(500L)).willReturn(Optional.of(booking));
        given(bookingItemRepository.findByBookingId(500L)).willReturn(List.of());
        given(scheduleRepository.findById(20L)).willReturn(Optional.of(newSchedule));
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));
        given(seatHoldService.holdSeat(5L, user)).willReturn(newSeat);
        given(pricingService.calculateSeatFare(newSchedule, newSeat)).willReturn(new BigDecimal("20.00"));
        given(pricingService.applyPromoCode("SAVE10", new BigDecimal("20.00")))
                .willReturn(new PromoCodeApplication(promoCode, new BigDecimal("2.00")));
        given(bookingItemRepository.save(any(BookingItem.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(bookingMapper.toResponse(booking)).willReturn(
                new BookingResponse(500L, 1L, 20L, "ABC234", Instant.now(), BookingStatus.INITIATED,
                        new BigDecimal("18.00"), "SAVE10"));
        given(bookingItemMapper.toResponse(any(BookingItem.class))).willAnswer(invocation -> {
            BookingItem item = invocation.getArgument(0);
            return new BookingItemResponse(null, 500L, item.getSeat().getId(), item.getPassenger().getId(), item.getFare());
        });

        RescheduleRequest request = new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)));
        bookingService.rescheduleBooking(500L, request);

        assertThat(booking.getTotalAmount()).isEqualByComparingTo("18.00");
    }
}
