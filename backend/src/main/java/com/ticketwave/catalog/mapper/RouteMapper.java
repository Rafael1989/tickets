package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RouteMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "operator", source = "operator")
    Route toEntity(RouteRequest request, User operator);

    @Mapping(target = "operatorId", source = "operator.id")
    RouteResponse toResponse(Route route);
}
