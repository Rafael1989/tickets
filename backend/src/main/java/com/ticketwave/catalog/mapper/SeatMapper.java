package com.ticketwave.catalog.mapper;

import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SeatMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "schedule", source = "schedule")
    @Mapping(target = "status", source = "request.status")
    @Mapping(target = "heldUntil", ignore = true)
    Seat toEntity(SeatRequest request, Schedule schedule);

    @Mapping(target = "scheduleId", source = "schedule.id")
    SeatResponse toResponse(Seat seat);
}
