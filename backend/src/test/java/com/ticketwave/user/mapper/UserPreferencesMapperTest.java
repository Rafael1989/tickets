package com.ticketwave.user.mapper;

import com.ticketwave.user.dto.UserPreferencesRequest;
import com.ticketwave.user.dto.UserPreferencesResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserPreferences;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserPreferencesMapperTest {

    private final UserPreferencesMapper mapper = new UserPreferencesMapperImpl();

    @Test
    void toResponse_flattensFields() {
        Instant updatedAt = Instant.parse("2026-01-01T00:00:00Z");
        UserPreferences preferences = UserPreferences.builder()
                .userId(1L)
                .preferredCurrency("EUR")
                .seatPreference("WINDOW")
                .notificationsEnabled(false)
                .updatedAt(updatedAt)
                .build();

        UserPreferencesResponse response = mapper.toResponse(preferences);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.preferredCurrency()).isEqualTo("EUR");
        assertThat(response.seatPreference()).isEqualTo("WINDOW");
        assertThat(response.notificationsEnabled()).isFalse();
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void updateEntity_overwritesFieldsButLeavesUserIdAndUserUntouched() {
        User user = User.builder().id(1L).build();
        UserPreferences preferences = UserPreferences.builder()
                .userId(1L)
                .user(user)
                .preferredCurrency("USD")
                .seatPreference(null)
                .notificationsEnabled(true)
                .build();
        UserPreferencesRequest request = new UserPreferencesRequest("EUR", "AISLE", false);

        mapper.updateEntity(request, preferences);

        assertThat(preferences.getUserId()).isEqualTo(1L);
        assertThat(preferences.getUser()).isEqualTo(user);
        assertThat(preferences.getPreferredCurrency()).isEqualTo("EUR");
        assertThat(preferences.getSeatPreference()).isEqualTo("AISLE");
        assertThat(preferences.isNotificationsEnabled()).isFalse();
    }
}
