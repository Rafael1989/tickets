package com.ticketwave.user.service;

import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;
import com.ticketwave.user.entity.Passenger;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.mapper.PassengerMapper;
import com.ticketwave.user.repository.PassengerRepository;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PassengerServiceImpl implements PassengerService {

    private final PassengerRepository passengerRepository;
    private final UserRepository userRepository;
    private final PassengerMapper passengerMapper;

    public PassengerServiceImpl(
            PassengerRepository passengerRepository,
            UserRepository userRepository,
            PassengerMapper passengerMapper
    ) {
        this.passengerRepository = passengerRepository;
        this.userRepository = userRepository;
        this.passengerMapper = passengerMapper;
    }

    @Override
    @Transactional
    public PassengerResponse createPassenger(String username, PassengerRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        Passenger passenger = passengerRepository.save(passengerMapper.toEntity(request, user));
        return passengerMapper.toResponse(passenger);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassengerResponse> listMyPassengers(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        return passengerRepository.findByUserId(user.getId()).stream()
                .map(passengerMapper::toResponse)
                .toList();
    }
}
