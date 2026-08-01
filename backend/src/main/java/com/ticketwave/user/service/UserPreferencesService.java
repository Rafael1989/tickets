package com.ticketwave.user.service;

import com.ticketwave.user.dto.UserPreferencesRequest;
import com.ticketwave.user.dto.UserPreferencesResponse;

public interface UserPreferencesService {

    /**
     * Returns the given (authenticated) username's preferences, creating a
     * default row (USD, no seat preference, notifications on) on first
     * access — most accounts, including every one seeded before this
     * feature existed, won't have a row yet.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if username doesn't resolve to a user
     */
    UserPreferencesResponse getOrCreateDefault(String username);

    /**
     * Replaces the given (authenticated) username's preferences, creating
     * the row if it doesn't exist yet.
     *
     * @throws com.ticketwave.user.exception.UserNotFoundException if username doesn't resolve to a user
     */
    UserPreferencesResponse update(String username, UserPreferencesRequest request);
}
