package com.ticketwave.user.service;

import com.ticketwave.user.dto.PassengerRequest;
import com.ticketwave.user.dto.PassengerResponse;

import java.util.List;

public interface PassengerService {

    /**
     * Creates a passenger profile owned by the given (authenticated)
     * username.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if username doesn't resolve to a user
     */
    PassengerResponse createPassenger(String username, PassengerRequest request);

    /**
     * Lists every passenger profile owned by the given (authenticated)
     * username.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if username doesn't resolve to a user
     */
    List<PassengerResponse> listMyPassengers(String username);
}
