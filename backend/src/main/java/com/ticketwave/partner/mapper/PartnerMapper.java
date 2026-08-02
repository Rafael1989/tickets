package com.ticketwave.partner.mapper;

import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.entity.Partner;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PartnerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Partner toEntity(PartnerRequest request);

    PartnerResponse toResponse(Partner partner);
}
