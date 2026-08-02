package com.ticketwave.partner.mapper;

import com.ticketwave.partner.dto.PartnerCredentialResponse;
import com.ticketwave.partner.entity.PartnerApiCredential;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PartnerCredentialMapper {

    @Mapping(target = "partnerId", source = "partner.id")
    PartnerCredentialResponse toResponse(PartnerApiCredential credential);
}
