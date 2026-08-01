package com.ticketwave.pricing.mapper;

import com.ticketwave.catalog.entity.Route;
import com.ticketwave.pricing.dto.FareRuleRequest;
import com.ticketwave.pricing.dto.FareRuleResponse;
import com.ticketwave.pricing.entity.FareRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface FareRuleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "route", source = "route")
    FareRule toEntity(FareRuleRequest request, Route route);

    @Mapping(target = "routeId", source = "route.id")
    FareRuleResponse toResponse(FareRule fareRule);
}
