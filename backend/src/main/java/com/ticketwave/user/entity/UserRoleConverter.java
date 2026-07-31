package com.ticketwave.user.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class UserRoleConverter extends CodedEnumConverter<UserRole> {

    public UserRoleConverter() {
        super(UserRole.class);
    }
}
