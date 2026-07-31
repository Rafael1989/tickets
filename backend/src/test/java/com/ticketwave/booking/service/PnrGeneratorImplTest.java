package com.ticketwave.booking.service;

import com.ticketwave.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PnrGeneratorImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Test
    void generate_returnsSixCharacterCodeFromTheUnambiguousAlphabet() {
        PnrGeneratorImpl generator = new PnrGeneratorImpl(bookingRepository);
        given(bookingRepository.existsByPnr(org.mockito.ArgumentMatchers.anyString())).willReturn(false);

        String pnr = generator.generate();

        assertThat(pnr).hasSize(6);
        assertThat(pnr).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{6}");
        assertThat(pnr).doesNotContainPattern("[O0I1]");
    }

    @Test
    void generate_retriesWhenACandidateCollides() {
        PnrGeneratorImpl generator = new PnrGeneratorImpl(bookingRepository);
        given(bookingRepository.existsByPnr(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(true, true, false);

        String pnr = generator.generate();

        assertThat(pnr).hasSize(6);
        verify(bookingRepository, times(3)).existsByPnr(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void generate_whenEveryAttemptCollides_throwsIllegalStateException() {
        PnrGeneratorImpl generator = new PnrGeneratorImpl(bookingRepository);
        given(bookingRepository.existsByPnr(org.mockito.ArgumentMatchers.anyString())).willReturn(true);

        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class);
    }
}
