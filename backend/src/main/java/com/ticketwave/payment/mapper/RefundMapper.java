package com.ticketwave.payment.mapper;

import com.ticketwave.payment.dto.RefundRequest;
import com.ticketwave.payment.dto.RefundResponse;
import com.ticketwave.payment.entity.Payment;
import com.ticketwave.payment.entity.Refund;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RefundMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "amount", source = "request.amount")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "processedBy", ignore = true)
    @Mapping(target = "processedAt", ignore = true)
    Refund toEntity(RefundRequest request, Payment payment);

    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "processedByUserId", source = "processedBy.id")
    RefundResponse toResponse(Refund refund);
}
