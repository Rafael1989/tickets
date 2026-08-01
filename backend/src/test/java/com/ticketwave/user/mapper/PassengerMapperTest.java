package com.ticketwave.user.mapper;

import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PassengerMapperTest {

    private final PassengerMapper mapper = new PassengerMapperImpl();

    @Test
    void toEntity_setsUserAndCopiesRequestFields() {
        User user = User.builder().id(1L).build();
        PassengerRequest request = new PassengerRequest("Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");

        Passenger passenger = mapper.toEntity(request, user);

        assertThat(passenger.getUser()).isEqualTo(user);
        assertThat(passenger.getFullName()).isEqualTo("Jane Doe");
        assertThat(passenger.getDob()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(passenger.getIdType()).isEqualTo("passport");
        assertThat(passenger.getIdNumber()).isEqualTo("X123456");
        assertThat(passenger.getId()).isNull();
    }

    @Test
    void toEntity_whenBothArgumentsAreNull_returnsNull() {
        assertThat(mapper.toEntity(null, null)).isNull();
    }

    @Test
    void toEntity_whenRequestIsNull_stillSetsUserOnly() {
        User user = User.builder().id(1L).build();

        Passenger passenger = mapper.toEntity(null, user);

        assertThat(passenger.getUser()).isEqualTo(user);
        assertThat(passenger.getFullName()).isNull();
    }

    @Test
    void toResponse_whenNull_returnsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }

    @Test
    void toResponse_whenUserIsNull_leavesUserIdNull() {
        Passenger passenger = Passenger.builder().id(100L).build();

        PassengerResponse response = mapper.toResponse(passenger);

        assertThat(response.userId()).isNull();
    }

    @Test
    void toResponse_flattensUserId() {
        Passenger passenger = Passenger.builder()
                .id(100L)
                .user(User.builder().id(1L).build())
                .fullName("Jane Doe")
                .dob(LocalDate.of(1990, 1, 1))
                .idType("passport")
                .idNumber("X123456")
                .build();

        PassengerResponse response = mapper.toResponse(passenger);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.fullName()).isEqualTo("Jane Doe");
        assertThat(response.idNumber()).isEqualTo("X123456");
    }

    @Test
    void updateEntity_overwritesFieldsButLeavesIdAndUserUntouched() {
        User user = User.builder().id(1L).build();
        Passenger passenger = Passenger.builder()
                .id(100L)
                .user(user)
                .fullName("Old Name")
                .dob(LocalDate.of(1980, 1, 1))
                .idType("passport")
                .idNumber("OLD123")
                .build();
        PassengerRequest request = new PassengerRequest("New Name", LocalDate.of(1990, 1, 1), "national_id", "NEW456");

        mapper.updateEntity(request, passenger);

        assertThat(passenger.getId()).isEqualTo(100L);
        assertThat(passenger.getUser()).isEqualTo(user);
        assertThat(passenger.getFullName()).isEqualTo("New Name");
        assertThat(passenger.getDob()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(passenger.getIdType()).isEqualTo("national_id");
        assertThat(passenger.getIdNumber()).isEqualTo("NEW456");
    }
}
