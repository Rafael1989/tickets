package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.ScheduleRequest;
import com.ticketwave.catalog.dto.ScheduleResponse;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ScheduleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "route", source = "route")
    Schedule toEntity(ScheduleRequest request, Route route);

    @Mapping(target = "routeId", source = "route.id")
    ScheduleResponse toResponse(Schedule schedule);
}
