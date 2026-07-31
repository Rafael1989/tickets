package com.ticketwave.catalog.entity;

import com.ticketwave.common.persistence.CodedEnumConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RouteTypeConverter extends CodedEnumConverter<RouteType> {

    public RouteTypeConverter() {
        super(RouteType.class);
    }
}
