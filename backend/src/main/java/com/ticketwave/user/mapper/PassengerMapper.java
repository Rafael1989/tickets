package com.ticketwave.user.mapper;

import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * The service resolves the authenticated caller's username to a
 * {@link User} via UserRepository and passes it in; the mapper only
 * assigns it, it never performs the lookup itself.
 */
@Mapper(componentModel = "spring")
public interface PassengerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    Passenger toEntity(PassengerRequest request, User user);

    @Mapping(target = "userId", source = "user.id")
    PassengerResponse toResponse(Passenger passenger);
}
