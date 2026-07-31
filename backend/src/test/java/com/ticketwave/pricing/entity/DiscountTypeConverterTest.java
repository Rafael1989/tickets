package com.ticketwave.pricing.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiscountTypeConverterTest {

    private final DiscountTypeConverter converter = new DiscountTypeConverter();

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_everyConstant_returnsItsCode() {
        for (DiscountType type : DiscountType.values()) {
            assertThat(converter.convertToDatabaseColumn(type)).isEqualTo(type.getCode());
        }
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_everyCode_returnsMatchingConstant() {
        for (DiscountType type : DiscountType.values()) {
            assertThat(converter.convertToEntityAttribute(type.getCode())).isEqualTo(type);
        }
    }

    @Test
    void convertToEntityAttribute_unknownCode_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-real-code"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
