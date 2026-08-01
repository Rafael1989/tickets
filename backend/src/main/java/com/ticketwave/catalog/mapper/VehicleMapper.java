package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.VehicleRequest;
import com.ticketwave.catalog.dto.VehicleResponse;
import com.ticketwave.catalog.entity.Vehicle;
import com.ticketwave.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface VehicleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "operator", source = "operator")
    Vehicle toEntity(VehicleRequest request, User operator);

    @Mapping(target = "operatorId", source = "operator.id")
    VehicleResponse toResponse(Vehicle vehicle);
}
