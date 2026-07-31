package com.ticketwave.user.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRoleConverterTest {

    private final UserRoleConverter converter = new UserRoleConverter();

    @Test
    void convertToDatabaseColumn_null_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_everyConstant_returnsItsCode() {
        for (UserRole role : UserRole.values()) {
            assertThat(converter.convertToDatabaseColumn(role)).isEqualTo(role.getCode());
        }
    }

    @Test
    void convertToEntityAttribute_null_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_everyCode_returnsMatchingConstant() {
        for (UserRole role : UserRole.values()) {
            assertThat(converter.convertToEntityAttribute(role.getCode())).isEqualTo(role);
        }
    }

    @Test
    void convertToEntityAttribute_unknownCode_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("not-a-real-code"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
