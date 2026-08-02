package com.ticketwave.user.mapper;

import com.ticketwave.partner.entity.Partner;
import com.ticketwave.user.dto.UserRequest;
import com.ticketwave.user.dto.UserResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper mapper = new UserMapperImpl();

    @Test
    void toEntity_copiesUsernameEmailRole_ignoresPasswordHashAndCreatedAt() {
        UserRequest request = new UserRequest("alice", "password123", "alice@example.com", UserRole.CUSTOMER, null);

        User user = mapper.toEntity(request);

        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getRole()).isEqualTo(UserRole.CUSTOMER);
        assertThat(user.getId()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getCreatedAt()).isNull();
    }

    @Test
    void toEntity_ignoresPartnerEvenWhenPartnerIdIsSupplied() {
        UserRequest request = new UserRequest("operator1", "password123", "operator1@example.com", UserRole.OPERATOR, 9L);

        User user = mapper.toEntity(request);

        assertThat(user.getPartner()).isNull();
    }

    @Test
    void toEntity_whenNull_returnsNull() {
        assertThat(mapper.toEntity(null)).isNull();
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_copiesFieldsAndNeverExposesPasswordHash() {
        User user = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hashed")
                .role(UserRole.CUSTOMER)
                .createdAt(Instant.parse("2026-08-01T00:00:00Z"))
                .build();

        UserResponse response = mapper.toResponse(user);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.role()).isEqualTo(UserRole.CUSTOMER);
        assertThat(response.partnerId()).isNull();
        assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }

    @Test
    void toResponse_flattensThePartnerIdWhenPresent() {
        User operator = User.builder().id(2L).username("operator1").role(UserRole.OPERATOR)
                .partner(Partner.builder().id(9L).build()).createdAt(Instant.now()).build();

        UserResponse response = mapper.toResponse(operator);

        assertThat(response.partnerId()).isEqualTo(9L);
    }
}
