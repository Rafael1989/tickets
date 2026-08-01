package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.entity.Driver;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "route", source = "route")
    @Mapping(target = "vehicle", source = "vehicle")
    @Mapping(target = "driver", source = "driver")
    Schedule toEntity(ScheduleRequest request, Route route, Vehicle vehicle, Driver driver);

    @Mapping(target = "routeId", source = "route.id")
    @Mapping(target = "vehicleId", source = "vehicle.id")
    @Mapping(target = "driverId", source = "driver.id")
    ScheduleResponse toResponse(Schedule schedule);
}
