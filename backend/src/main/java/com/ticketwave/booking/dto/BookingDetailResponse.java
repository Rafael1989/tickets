package com.ticketwave.booking.dto;

import java.util.List;

public record BookingDetailResponse(
        BookingResponse booking,
        List<BookingItemResponse> items
) {
}
