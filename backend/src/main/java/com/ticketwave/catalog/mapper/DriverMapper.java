package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.DriverRequest;
import com.ticketwave.catalog.dto.DriverResponse;
import com.ticketwave.catalog.entity.Driver;
import com.ticketwave.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DriverMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "operator", source = "operator")
    Driver toEntity(DriverRequest request, User operator);

    @Mapping(target = "operatorId", source = "operator.id")
    DriverResponse toResponse(Driver driver);
}
