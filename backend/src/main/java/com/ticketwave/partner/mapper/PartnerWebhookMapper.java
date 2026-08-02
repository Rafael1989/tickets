package com.ticketwave.partner.mapper;

import com.ticketwave.partner.dto.PartnerWebhookResponse;
import com.ticketwave.partner.entity.PartnerWebhook;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PartnerWebhookMapper {

    @Mapping(target = "partnerId", source = "partner.id")
    PartnerWebhookResponse toResponse(PartnerWebhook webhook);
}
