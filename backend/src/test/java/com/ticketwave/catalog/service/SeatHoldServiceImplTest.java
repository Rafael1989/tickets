package com.ticketwave.catalog.service;

import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.InventoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SeatHoldServiceImplTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private InventoryProperties inventoryProperties;

    @InjectMocks
    private SeatHoldServiceImpl seatHoldService;

    private static Seat seat(SeatStatus status, Instant heldUntil) {
        return Seat.builder().id(1L).status(status).heldUntil(heldUntil).build();
    }

    @Test
    void holdSeat_whenAvailable_marksHeldWithExpiration() {
        Seat seat = seat(SeatStatus.AVAILABLE, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));
        given(inventoryProperties.seatHoldTtlMinutes()).willReturn(10L);

        Instant before = Instant.now();
        Seat result = seatHoldService.holdSeat(1L);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHeldUntil()).isAfter(before);
    }

    @Test
    void holdSeat_whenHeldAndNotExpired_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(600));
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.holdSeat(1L))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void holdSeat_whenHeldButExpired_reclaimsSeatForNewHold() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().minusSeconds(1));
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));
        given(inventoryProperties.seatHoldTtlMinutes()).willReturn(10L);

        Seat result = seatHoldService.holdSeat(1L);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHeldUntil()).isAfter(Instant.now());
    }

    @Test
    void holdSeat_whenBooked_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.BOOKED, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.holdSeat(1L))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void holdSeat_whenSeatMissing_throwsSeatNotFoundException() {
        given(seatRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatHoldService.holdSeat(99L))
                .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void releaseSeat_whenHeld_setsAvailableAndClearsHeldUntil() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(300));
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseSeat(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHeldUntil()).isNull();
    }

    @Test
    void releaseSeat_whenBooked_setsAvailable() {
        // Cancelling an already-CONFIRMED (paid) booking must free its
        // BOOKED seats back to inventory, not just HELD ones.
        Seat seat = seat(SeatStatus.BOOKED, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseSeat(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void releaseSeat_whenAlreadyAvailable_isNoOp() {
        Seat seat = seat(SeatStatus.AVAILABLE, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseSeat(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void confirmHold_whenHeldAndNotExpired_marksBooked() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(300));
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.confirmHold(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getHeldUntil()).isNull();
    }

    @Test
    void confirmHold_whenHeldWithNoExpirationSet_marksBooked() {
        // Defensive branch: a HELD seat with a null heldUntil is never
        // treated as expired, since isExpiredHold short-circuits on the
        // null check before comparing instants.
        Seat seat = seat(SeatStatus.HELD, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.confirmHold(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void confirmHold_whenHoldExpired_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().minusSeconds(1));
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.confirmHold(1L))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void confirmHold_whenNotHeld_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.AVAILABLE, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.confirmHold(1L))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void releaseExpiredHolds_delegatesToRepositoryBulkUpdate() {
        given(seatRepository.releaseExpiredHolds(org.mockito.ArgumentMatchers.any())).willReturn(3);

        int released = seatHoldService.releaseExpiredHolds();

        assertThat(released).isEqualTo(3);
    }
}
