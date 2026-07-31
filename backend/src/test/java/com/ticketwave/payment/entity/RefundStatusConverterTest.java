package com.ticketwave.payment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundStatusConverterTest {

    private final RefundStatusConverter converter = new RefundStatusConverter();

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_everyConstant_returnsItsCode() {
        for (RefundStatus status : RefundStatus.values()) {
            assertThat(converter.convertToDatabaseColumn(status)).isEqualTo(status.getCode());
        }
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_everyCode_returnsMatchingConstant() {
        for (RefundStatus status : RefundStatus.values()) {
            assertThat(converter.convertToEntityAttribute(status.getCode())).isEqualTo(status);
        }
    }

    @Test
    void convertToEntityAttribute_unknownCode_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-real-code"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
