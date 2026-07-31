package com.ticketwave.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.auth.JwtService;
import com.ticketwave.auth.security.JwtAuthenticationFilter;
import com.ticketwave.booking.dto.BookingDetailResponse;
import com.ticketwave.booking.dto.BookingResponse;
import com.ticketwave.booking.dto.CreateBookingRequest;
import com.ticketwave.booking.dto.RescheduleRequest;
import com.ticketwave.booking.dto.SeatSelection;
import com.ticketwave.booking.entity.BookingStatus;
import com.ticketwave.booking.service.BookingService;
import com.ticketwave.config.JwtProperties;
import com.ticketwave.config.SecurityConfig;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.PaymentStatus;
import com.ticketwave.payment.entity.RefundStatus;
import com.ticketwave.payment.service.PaymentService;
import com.ticketwave.payment.service.RefundService;
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
        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null);

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBooking_withValidTokenAndPayload_returns201() throws Exception {
        CreateBookingRequest request = new CreateBookingRequest(10L, List.of(new SeatSelection(5L, 100L)), null);
        given(bookingService.createBooking(eq("alice"), any())).willReturn(detailResponse());

        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.pnr").value("ABC234"));
    }

    @Test
    void recordPayment_withValidTokenAndPayload_returns201() throws Exception {
        PaymentRequest request = new PaymentRequest(new BigDecimal("50.00"), "card", "REF-1");
        PaymentResponse response = new PaymentResponse(1L, 500L, new BigDecimal("50.00"), "card", "REF-1",
                PaymentStatus.SUCCEEDED, Instant.now());
        given(paymentService.recordPayment(eq(500L), any())).willReturn(response);

        mockMvc.perform(post("/api/bookings/500/payments")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
    }

    @Test
    void initiateRefund_withValidToken_returns201() throws Exception {
        RefundResponse response = new RefundResponse(1L, 1L, new BigDecimal("50.00"), "FULL_REFUND",
                RefundStatus.PENDING, null, null);
        given(refundService.initiateRefund(500L)).willReturn(response);

        mockMvc.perform(post("/api/bookings/500/refunds").header("Authorization", bearerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
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
        given(bookingService.rescheduleBooking(eq(500L), any())).willReturn(detailResponse());

        mockMvc.perform(put("/api/bookings/500/reschedule")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.pnr").value("ABC234"));
    }
}
