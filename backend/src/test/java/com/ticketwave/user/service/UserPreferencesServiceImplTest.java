package com.ticketwave.user.service;

import com.ticketwave.user.dto.UserPreferencesRequest;
import com.ticketwave.user.dto.UserPreferencesResponse;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.entity.UserPreferences;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.mapper.UserPreferencesMapper;
import com.ticketwave.user.repository.UserPreferencesRepository;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserPreferencesServiceImplTest {

    @Mock
    private UserPreferencesRepository preferencesRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPreferencesMapper preferencesMapper;

    @InjectMocks
    private UserPreferencesServiceImpl preferencesService;

    @Test
    void getOrCreateDefault_whenPreferencesExist_returnsThemWithoutCreating() {
        User user = User.builder().id(1L).username("alice").build();
        UserPreferences existing = UserPreferences.builder().userId(1L).user(user).preferredCurrency("EUR").build();
        UserPreferencesResponse response = new UserPreferencesResponse(1L, "EUR", null, true, null);

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(preferencesRepository.findById(1L)).willReturn(Optional.of(existing));
        given(preferencesMapper.toResponse(existing)).willReturn(response);

        UserPreferencesResponse result = preferencesService.getOrCreateDefault("alice");

        assertThat(result).isEqualTo(response);
        verify(preferencesRepository, never()).save(any());
    }

    @Test
    void getOrCreateDefault_whenNoneExist_createsDefaultRow() {
        User user = User.builder().id(1L).username("alice").build();
        UserPreferences created = UserPreferences.builder().userId(1L).user(user).preferredCurrency("USD").notificationsEnabled(true).build();
        UserPreferencesResponse response = new UserPreferencesResponse(1L, "USD", null, true, null);

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(preferencesRepository.findById(1L)).willReturn(Optional.empty());
        given(preferencesRepository.save(any())).willReturn(created);
        given(preferencesMapper.toResponse(created)).willReturn(response);

        UserPreferencesResponse result = preferencesService.getOrCreateDefault("alice");

        assertThat(result).isEqualTo(response);

        ArgumentCaptor<UserPreferences> captor = ArgumentCaptor.forClass(UserPreferences.class);
        verify(preferencesRepository).save(captor.capture());
        assertThat(captor.getValue().getPreferredCurrency()).isEqualTo("USD");
        assertThat(captor.getValue().isNotificationsEnabled()).isTrue();
        assertThat(captor.getValue().getSeatPreference()).isNull();
    }

    @Test
    void getOrCreateDefault_whenUserMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> preferencesService.getOrCreateDefault("ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void update_whenPreferencesExist_updatesInPlaceAndSaves() {
        User user = User.builder().id(1L).username("alice").build();
        UserPreferences existing = UserPreferences.builder().userId(1L).user(user).preferredCurrency("USD").build();
        UserPreferencesRequest request = new UserPreferencesRequest("EUR", "AISLE", false);
        UserPreferencesResponse response = new UserPreferencesResponse(1L, "EUR", "AISLE", false, null);

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(preferencesRepository.findById(1L)).willReturn(Optional.of(existing));
        given(preferencesRepository.save(existing)).willReturn(existing);
        given(preferencesMapper.toResponse(existing)).willReturn(response);

        UserPreferencesResponse result = preferencesService.update("alice", request);

        assertThat(result).isEqualTo(response);
        verify(preferencesMapper).updateEntity(request, existing);
    }

    @Test
    void update_whenNoneExist_createsThenApplies() {
        User user = User.builder().id(1L).username("alice").build();
        UserPreferencesRequest request = new UserPreferencesRequest("EUR", "AISLE", false);
        UserPreferencesResponse response = new UserPreferencesResponse(1L, "EUR", "AISLE", false, null);

        given(userRepository.findByUsername("alice")).willReturn(Optional.of(user));
        given(preferencesRepository.findById(1L)).willReturn(Optional.empty());
        given(preferencesRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(preferencesMapper.toResponse(any())).willReturn(response);

        UserPreferencesResponse result = preferencesService.update("alice", request);

        assertThat(result).isEqualTo(response);
        verify(preferencesMapper).updateEntity(eq(request), any());
    }

    @Test
    void update_whenUserMissing_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());
        UserPreferencesRequest request = new UserPreferencesRequest("EUR", "AISLE", false);

        assertThatThrownBy(() -> preferencesService.update("ghost", request))
                .isInstanceOf(UserNotFoundException.class);
    }
}
