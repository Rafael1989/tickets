package com.ticketwave.booking.mapper;

import com.ticketwave.booking.dto.BookingItemRequest;
import com.ticketwave.booking.dto.BookingItemResponse;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.booking.entity.BookingItem;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.user.entity.Passenger;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingItemMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "booking", source = "booking")
    @Mapping(target = "seat", source = "seat")
    @Mapping(target = "passenger", source = "passenger")
    @Mapping(target = "fare", ignore = true)
    BookingItem toEntity(BookingItemRequest request, Booking booking, Seat seat, Passenger passenger);

    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "seatId", source = "seat.id")
    @Mapping(target = "passengerId", source = "passenger.id")
    BookingItemResponse toResponse(BookingItem bookingItem);
}
