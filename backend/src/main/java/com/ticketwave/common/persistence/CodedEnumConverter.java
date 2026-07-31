package com.ticketwave.common.persistence;

import jakarta.persistence.AttributeConverter;

/**
 * Shared JPA converter logic for {@link CodedEnum} implementations. Each
 * enum gets its own {@code @Converter(autoApply = true)} subclass so Hibernate
 * applies it automatically to every field of that enum type.
 */
public abstract class CodedEnumConverter<E extends Enum<E> & CodedEnum> implements AttributeConverter<E, String> {

    private final Class<E> enumType;

    protected CodedEnumConverter(Class<E> enumType) {
        this.enumType = enumType;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        for (E constant : enumType.getEnumConstants()) {
            if (constant.getCode().equals(dbData)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown code '" + dbData + "' for " + enumType.getSimpleName());
    }
}
