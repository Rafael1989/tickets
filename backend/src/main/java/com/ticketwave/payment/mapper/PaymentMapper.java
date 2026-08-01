package com.ticketwave.payment.mapper;

import com.ticketwave.booking.entity.Booking;
import com.ticketwave.payment.dto.PaymentRequest;
import com.ticketwave.payment.dto.PaymentResponse;
import com.ticketwave.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "booking", source = "booking")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    Payment toEntity(PaymentRequest request, Booking booking);

    @Mapping(target = "bookingId", source = "booking.id")
    PaymentResponse toResponse(Payment payment);
}
