package com.ticketwave.user.service;

import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.mapper.PassengerMapper;
import com.ticketwave.user.repository.PassengerRepository;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PassengerServiceImplTest {

    @Mock
    private PassengerRepository passengerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PassengerMapper passengerMapper;

    @InjectMocks
    private PassengerServiceImpl passengerService;

    @Test
    void createPassenger_whenUserFound_savesAndReturnsResponse() {
        User user = User.builder().id(1L).username("alice").build();
        PassengerRequest request = new PassengerRequest("Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");
        Passenger entity = Passenger.builder().user(user).fullName("Jane Doe").build();
        Passenger saved = Passenger.builder().id(100L).user(user).fullName("Jane Doe").build();
        PassengerResponse response = new PassengerResponse(100L, 1L, "Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(passengerMapper.toEntity(request, user)).willReturn(entity);
        given(passengerRepository.save(entity)).willReturn(saved);
        given(passengerMapper.toResponse(saved)).willReturn(response);

        PassengerResponse result = passengerService.createPassenger("alice", request);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void createPassenger_whenUserMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());
        PassengerRequest request = new PassengerRequest("Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");

        assertThatThrownBy(() -> passengerService.createPassenger("ghost", request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void listMyPassengers_whenUserFound_returnsMappedResponses() {
        User user = User.builder().id(1L).username("alice").build();
        Passenger passenger = Passenger.builder().id(100L).user(user).fullName("Jane Doe").build();
        PassengerResponse response = new PassengerResponse(100L, 1L, "Jane Doe", LocalDate.of(1990, 1, 1), "passport", "X123456");

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(passengerRepository.findByUserId(1L)).willReturn(List.of(passenger));
        given(passengerMapper.toResponse(passenger)).willReturn(response);

        List<PassengerResponse> result = passengerService.listMyPassengers("alice");

        assertThat(result).containsExactly(response);
    }

    @Test
    void listMyPassengers_whenUserMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> passengerService.listMyPassengers("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }
}
