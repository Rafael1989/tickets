package com.ticketwave.user.mapper;

import com.ticketwave.user.dto.UserRequest;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "partner", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(UserRequest request);

    @Mapping(target = "partnerId", source = "partner.id")
    UserResponse toResponse(User user);
}
