package com.ticketwave.user.service;

import com.ticketwave.user.dto.UserPreferencesRequest;
import com.ticketwave.user.dto.UserPreferencesResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserPreferences;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.mapper.UserPreferencesMapper;
import com.ticketwave.user.repository.UserPreferencesRepository;
import com.ticketwave.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferencesServiceImpl implements UserPreferencesService {

    private static final String DEFAULT_CURRENCY = "USD";

    private final UserPreferencesRepository preferencesRepository;
    private final UserRepository userRepository;
    private final UserPreferencesMapper preferencesMapper;

    public UserPreferencesServiceImpl(
            UserPreferencesRepository preferencesRepository,
            UserRepository userRepository,
            UserPreferencesMapper preferencesMapper
    ) {
        this.preferencesRepository = preferencesRepository;
        this.userRepository = userRepository;
        this.preferencesMapper = preferencesMapper;
    }

    @Override
    @Transactional
    public UserPreferencesResponse getOrCreateDefault(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        UserPreferences preferences = preferencesRepository.findById(user.getId())
                .orElseGet(() -> preferencesRepository.save(defaultPreferences(user)));

        return preferencesMapper.toResponse(preferences);
    }

    @Override
    @Transactional
    public UserPreferencesResponse update(String username, UserPreferencesRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        UserPreferences preferences = preferencesRepository.findById(user.getId())
                .orElseGet(() -> defaultPreferences(user));
        preferencesMapper.updateEntity(request, preferences);

        return preferencesMapper.toResponse(preferencesRepository.save(preferences));
    }

    private UserPreferences defaultPreferences(User user) {
        return UserPreferences.builder()
                .user(user)
                .preferredCurrency(DEFAULT_CURRENCY)
                .seatPreference(null)
                .notificationsEnabled(true)
                .build();
    }
}
