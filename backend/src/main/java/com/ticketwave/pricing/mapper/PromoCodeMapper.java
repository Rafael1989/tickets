package com.ticketwave.pricing.mapper;

import com.ticketwave.pricing.dto.PromoCodeRequest;
import com.ticketwave.pricing.dto.PromoCodeResponse;
import com.ticketwave.pricing.entity.PromoCode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PromoCodeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "redemptionCount", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    PromoCode toEntity(PromoCodeRequest request);

    PromoCodeResponse toResponse(PromoCode promoCode);
}
