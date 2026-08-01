package com.ticketwave.user.service;

import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.PassengerNotFoundException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    @Test
    void updatePassenger_whenOwnedByCaller_updatesAndReturnsResponse() {
        User user = User.builder().id(1L).username("alice").build();
        Passenger passenger = Passenger.builder().id(100L).user(user).fullName("Old Name").build();
        PassengerRequest request = new PassengerRequest("New Name", LocalDate.of(1990, 1, 1), "passport", "X999");
        PassengerResponse response = new PassengerResponse(100L, 1L, "New Name", LocalDate.of(1990, 1, 1), "passport", "X999");

        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));
        given(passengerMapper.toResponse(passenger)).willReturn(response);

        PassengerResponse result = passengerService.updatePassenger("alice", 100L, request);

        assertThat(result).isEqualTo(response);
        verify(passengerMapper).updateEntity(request, passenger);
    }

    @Test
    void updatePassenger_whenNotFound_throwsPassengerNotFoundException() {
        given(passengerRepository.findById(999L)).willReturn(Optional.empty());
        PassengerRequest request = new PassengerRequest("New Name", LocalDate.of(1990, 1, 1), "passport", "X999");

        assertThatThrownBy(() -> passengerService.updatePassenger("alice", 999L, request))
                .isInstanceOf(PassengerNotFoundException.class);
    }

    @Test
    void updatePassenger_whenOwnedBySomeoneElse_throwsPassengerNotFoundExceptionAndDoesNotUpdate() {
        User owner = User.builder().id(2L).username("bob").build();
        Passenger passenger = Passenger.builder().id(100L).user(owner).fullName("Bob's Passenger").build();
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));
        PassengerRequest request = new PassengerRequest("New Name", LocalDate.of(1990, 1, 1), "passport", "X999");

        assertThatThrownBy(() -> passengerService.updatePassenger("alice", 100L, request))
                .isInstanceOf(PassengerNotFoundException.class);

        verify(passengerMapper, never()).updateEntity(any(), any());
    }

    @Test
    void deletePassenger_whenOwnedByCaller_deletesIt() {
        User user = User.builder().id(1L).username("alice").build();
        Passenger passenger = Passenger.builder().id(100L).user(user).build();
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));

        passengerService.deletePassenger("alice", 100L);

        verify(passengerRepository).delete(passenger);
    }

    @Test
    void deletePassenger_whenNotFound_throwsPassengerNotFoundException() {
        given(passengerRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> passengerService.deletePassenger("alice", 999L))
                .isInstanceOf(PassengerNotFoundException.class);
    }

    @Test
    void deletePassenger_whenOwnedBySomeoneElse_throwsPassengerNotFoundExceptionAndDoesNotDelete() {
        User owner = User.builder().id(2L).username("bob").build();
        Passenger passenger = Passenger.builder().id(100L).user(owner).build();
        given(passengerRepository.findById(100L)).willReturn(Optional.of(passenger));

        assertThatThrownBy(() -> passengerService.deletePassenger("alice", 100L))
                .isInstanceOf(PassengerNotFoundException.class);

        verify(passengerRepository, never()).delete(any());
    }
}
