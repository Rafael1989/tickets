package com.ticketwave.booking.mapper;

import com.ticketwave.booking.dto.BookingRequest;
import com.ticketwave.booking.dto.BookingResponse;
import com.ticketwave.booking.entity.Booking;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookingMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "schedule", source = "schedule")
    @Mapping(target = "pnr", ignore = true)
    @Mapping(target = "bookingTime", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "promoCode", ignore = true)
    Booking toEntity(BookingRequest request, User user, Schedule schedule);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "scheduleId", source = "schedule.id")
    @Mapping(target = "promoCode", source = "promoCode.code")
    BookingResponse toResponse(Booking booking);
}
