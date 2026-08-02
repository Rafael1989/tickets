package com.ticketwave.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.BookingResponse;
import com.ticketwave.booking.dto.BookingSearchResult;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.RescheduleQuoteResponse;
import com.ticketwave.booking.dto.RescheduleRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.exception.InvalidBookingStateException;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.dto.RefundQuoteResponse;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.payment.service.PaymentService;
import com.ticketwave.payment.service.RefundService;
import com.ticketwave.payment.service.RescheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms POST /api/bookings/** requires authentication (unlike the public
 * catalog endpoints) and that valid requests wire through to the mocked
 * services correctly.
 */
@WebMvcTest(BookingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class})
@EnableConfigurationProperties(JwtProperties.class)
@TestPropertySource(properties = {
        "ticketwave.jwt.secret=test-only-secret-key-at-least-32-bytes-long",
        "ticketwave.jwt.access-token-ttl-minutes=15",
        "ticketwave.jwt.refresh-token-ttl-minutes=10080"
})
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private BookingService bookingService;
    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private RefundService refundService;
    @MockitoBean
    private RescheduleService rescheduleService;

    private String bearerToken;

    @BeforeEach
    void issueToken() {
        bearerToken = "Bearer " + jwtService.generateAccessToken("alice", List.of("CUSTOMER"));
    }

    private static BookingDetailResponse detailResponse() {
        return new BookingDetailResponse(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.INITIATED,
                        new BigDecimal("50.00"), null),
                List.of());
    }

    @Test
    void createBooking_withoutAuthorizationHeader_isRejected() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null, null);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBooking_withValidTokenAndPayload_returns201() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null, null);
        given(bookingService.createBooking(eq("alice"), any())).willReturn(detailResponse());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.pnr").value("ABC234"));
    }

    @Test
    void confirmBooking_whenAlreadyConfirmed_returns200() throws Exception {
        BookingDetailResponse confirmed = new BookingDetailResponse(
                new BookingResponse(500L, 1L, 10L, "ABC234", Instant.now(), BookingStatus.CONFIRMED,
                        new BigDecimal("50.00"), null),
                List.of());
        given(bookingService.requireConfirmed(500L)).willReturn(confirmed);

        mockMvc.perform(put("/api/bookings/500/confirm").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.status").value("CONFIRMED"));
    }

    @Test
    void confirmBooking_whenNotYetConfirmed_returns409() throws Exception {
        given(bookingService.requireConfirmed(500L))
                .willThrow(new InvalidBookingStateException(500L, BookingStatus.INITIATED, BookingStatus.CONFIRMED));

        mockMvc.perform(put("/api/bookings/500/confirm").header("Authorization", bearerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_BOOKING_STATE"));
    }

    @Test
    void recordPayment_withValidTokenAndPayload_returns201() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1", "4242424242424242");
        PaymentResponse response = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentService.recordPayment(eq(500L), any())).willReturn(response);

        mockMvc.perform(post("/api/bookings/500/payments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    /**
     * Regression test: a 100%-off promo code produces a booking whose total is 0.00, and the
     * payment DTO's old @DecimalMin("0.01") rejected the only amount such a booking could ever be
     * paid with — leaving it permanently stuck in INITIATED.
     */
    @Test
    void recordPayment_withAZeroTotalFromAFullDiscount_isAcceptedRatherThanRejectedAsInvalid() throws Exception {
        PaymentRequest request = new PaymentRequest(BigDecimal.ZERO, "pix", "REF-FREE", null);
        PaymentResponse response = new PaymentResponse(1L, 500L, BigDecimal.ZERO, "pix", "REF-FREE",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentService.recordPayment(eq(500L), any())).willReturn(response);

        mockMvc.perform(post("/api/bookings/500/payments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void recordPayment_withANegativeAmount_isStillRejected() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("-1.00"), "pix", "REF-NEG", null);

        mockMvc.perform(post("/api/bookings/500/payments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmThreeDs_withValidToken_returns200() throws Exception {
        PaymentResponse response = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now(), null);
        given(paymentService.confirmThreeDs(500L, 1L, "123456")).willReturn(response);

        mockMvc.perform(post("/api/bookings/500/payments/1/confirm-3ds")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void previewRefund_withValidToken_returns200() throws Exception {
        RefundQuoteResponse response = new RefundQuoteResponse(500L, new BigDecimal("50.00"), "FULL_REFUND",
                BigDecimal.ONE, new BigDecimal("50.00"), BigDecimal.ZERO, "card", true);
        given(refundService.previewRefund(500L)).willReturn(response);

        mockMvc.perform(get("/api/bookings/500/refund-quote").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.refundAmount").value(50.00));
    }

    @Test
    void initiateRefund_withValidToken_returns201() throws Exception {
        RefundResponse response = new RefundResponse(1L, 1L, new BigDecimal("50.00"), "FULL_REFUND",
                RefundStatus.PENDING, null, null, null, null);
        given(refundService.initiateRefund(500L)).willReturn(response);

        mockMvc.perform(post("/api/bookings/500/refunds").header("Authorization", bearerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void listRefunds_withValidToken_returns200() throws Exception {
        RefundResponse refund = new RefundResponse(1L, 1L, new BigDecimal("50.00"), "FULL_REFUND",
                RefundStatus.PENDING, null, null, null, null);
        given(refundService.listRefundsForBooking(500L)).willReturn(List.of(refund));

        mockMvc.perform(get("/api/bookings/500/refunds").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getBooking_withValidToken_returns200() throws Exception {
        given(bookingService.getBooking(500L)).willReturn(detailResponse());

        mockMvc.perform(get("/api/bookings/500").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.pnr").value("ABC234"));
    }

    @Test
    void getBookingByPnr_withValidToken_returns200() throws Exception {
        given(bookingService.getBookingByPnr("ABC234")).willReturn(detailResponse());

        mockMvc.perform(get("/api/bookings/pnr/ABC234").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.pnr").value("ABC234"));
    }

    @Test
    void rescheduleBooking_withValidToken_returns200() throws Exception {
        RescheduleRequest request = new RescheduleRequest(20L, List.of(new SeatSelection(5L, 100L)));
        given(rescheduleService.reschedule(eq(500L), any())).willReturn(detailResponse());

        mockMvc.perform(put("/api/bookings/500/reschedule")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.pnr").value("ABC234"));
    }

    @Test
    void searchBookings_withValidToken_returns200() throws Exception {
        BookingSearchResult result = new BookingSearchResult(500L, "ABC234", BookingStatus.CONFIRMED,
                new BigDecimal("50.00"), Instant.now(), "alice", "alice@example.com", "NYC", "LAX", Instant.now());
        given(bookingService.searchBookings("alice")).willReturn(List.of(result));

        mockMvc.perform(get("/api/bookings/search").header("Authorization", bearerToken).param("query", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pnr").value("ABC234"))
                .andExpect(jsonPath("$[0].customerEmail").value("alice@example.com"));
    }

    @Test
    void lookupByPnrAndEmail_withoutAnyAuthorizationHeader_returns200() throws Exception {
        given(bookingService.lookupByPnrAndEmail("ABC234", "alice@example.com")).willReturn(detailResponse());

        mockMvc.perform(get("/api/bookings/pnr/ABC234/lookup").param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.pnr").value("ABC234"));
    }

    @Test
    void listMyBookings_withValidToken_returns200() throws Exception {
        BookingSearchResult result = new BookingSearchResult(500L, "ABC234", BookingStatus.CONFIRMED,
                new BigDecimal("50.00"), Instant.now(), "alice", "alice@example.com", "NYC", "LAX", Instant.now());
        given(bookingService.listMyBookings("alice")).willReturn(List.of(result));

        mockMvc.perform(get("/api/bookings/me").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].pnr").value("ABC234"));
    }

    @Test
    void listMyBookings_withoutAuthorizationHeader_isRejected() throws Exception {
        mockMvc.perform(get("/api/bookings/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void previewReschedule_withValidToken_returns200() throws Exception {
        RescheduleQuoteResponse response = new RescheduleQuoteResponse(500L, new BigDecimal("50.00"),
                new BigDecimal("65.00"), new BigDecimal("15.00"), true, true);
        given(rescheduleService.previewReschedule(500L, 20L, List.of(5L, 6L))).willReturn(response);

        mockMvc.perform(get("/api/bookings/500/reschedule-quote")
                        .header("Authorization", bearerToken)
                        .param("scheduleId", "20")
                        .param("seatIds", "5", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentRequired").value(true))
                .andExpect(jsonPath("$.fareDifference").value(15.00));
    }
}
