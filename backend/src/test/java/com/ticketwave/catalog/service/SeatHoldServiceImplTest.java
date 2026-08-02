package com.ticketwave.catalog.service;

import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.InventoryProperties;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.exception.UserNotFoundException;
import com.ticketwave.user.repository.UserRepository;
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
    private UserRepository userRepository;

    @Mock
    private InventoryProperties inventoryProperties;

    @InjectMocks
    private SeatHoldServiceImpl seatHoldService;

    private static final User ALICE = User.builder().id(1L).username("alice").build();
    private static final User BOB = User.builder().id(2L).username("bob").build();

    private static Seat seat(SeatStatus status, Instant heldUntil, User heldBy) {
        return Seat.builder().id(1L).status(status).heldUntil(heldUntil).heldBy(heldBy).build();
    }

    @Test
    void holdSeat_whenAvailable_marksHeldWithExpirationAndOwner() {
        Seat seat = seat(SeatStatus.AVAILABLE, null, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));
        given(inventoryProperties.seatHoldTtlMinutes()).willReturn(10L);

        Instant before = Instant.now();
        Seat result = seatHoldService.holdSeat(1L, ALICE);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHeldUntil()).isAfter(before);
        assertThat(result.getHeldBy()).isEqualTo(ALICE);
    }

    @Test
    void holdSeat_whenHeldBySomeoneElseAndNotExpired_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(600), BOB);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.holdSeat(1L, ALICE))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void holdSeat_whenHeldByCallerAndNotExpired_reaffirmsAndExtendsTtlInsteadOfThrowing() {
        Instant almostExpired = Instant.now().plusSeconds(5);
        Seat seat = seat(SeatStatus.HELD, almostExpired, ALICE);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));
        given(inventoryProperties.seatHoldTtlMinutes()).willReturn(10L);

        Seat result = seatHoldService.holdSeat(1L, ALICE);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHeldBy()).isEqualTo(ALICE);
        assertThat(result.getHeldUntil()).isAfter(almostExpired);
    }

    @Test
    void holdSeat_whenHeldButExpired_reclaimsSeatForNewHolder() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().minusSeconds(1), BOB);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));
        given(inventoryProperties.seatHoldTtlMinutes()).willReturn(10L);

        Seat result = seatHoldService.holdSeat(1L, ALICE);

        assertThat(result.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(result.getHeldUntil()).isAfter(Instant.now());
        assertThat(result.getHeldBy()).isEqualTo(ALICE);
    }

    @Test
    void holdSeat_whenHeldWithNullOwnerAndNotExpired_throwsSeatUnavailableException() {
        // Defensive branch: a HELD seat should never actually have a null
        // heldBy in practice (holdSeat always sets both together), but
        // heldByCaller must short-circuit to false rather than NPE if it does.
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(600), null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.holdSeat(1L, ALICE))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void holdSeat_whenBooked_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.BOOKED, null, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.holdSeat(1L, ALICE))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void holdSeat_whenSeatMissing_throwsSeatNotFoundException() {
        given(seatRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatHoldService.holdSeat(99L, ALICE))
                .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void holdSeatForUsername_resolvesUserThenDelegatesToHoldSeat() {
        Seat seat = seat(SeatStatus.AVAILABLE, null, null);
        given(userRepository.findByUsername("alice")).willReturn(Optional.of(ALICE));
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));
        given(inventoryProperties.seatHoldTtlMinutes()).willReturn(10L);

        Seat result = seatHoldService.holdSeatForUsername(1L, "alice");

        assertThat(result.getHeldBy()).isEqualTo(ALICE);
    }

    @Test
    void holdSeatForUsername_whenUsernameUnknown_throwsUserNotFoundException() {
        given(userRepository.findByUsername("ghost")).willReturn(Optional.empty());

        assertThatThrownBy(() -> seatHoldService.holdSeatForUsername(1L, "ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void releaseSeat_whenHeld_setsAvailableAndClearsHeldUntilAndOwner() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(300), ALICE);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseSeat(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHeldUntil()).isNull();
        assertThat(seat.getHeldBy()).isNull();
    }

    @Test
    void releaseSeat_whenBooked_setsAvailable() {
        // Cancelling an already-CONFIRMED (paid) booking must free its
        // BOOKED seats back to inventory, not just HELD ones.
        Seat seat = seat(SeatStatus.BOOKED, null, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseSeat(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void releaseSeat_whenAlreadyAvailable_isNoOp() {
        Seat seat = seat(SeatStatus.AVAILABLE, null, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseSeat(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void releaseOwnHold_whenHeldByCaller_releasesIt() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(300), ALICE);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseOwnHold(1L, "alice");

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getHeldBy()).isNull();
        assertThat(seat.getHeldUntil()).isNull();
    }

    @Test
    void releaseOwnHold_whenHeldBySomeoneElse_isSilentNoOp() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(300), BOB);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseOwnHold(1L, "alice");

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getHeldBy()).isEqualTo(BOB);
    }

    @Test
    void releaseOwnHold_whenHeldWithNullOwner_isSilentNoOp() {
        // Defensive branch: same null-heldBy short-circuit as holdSeat, on
        // the release path.
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(300), null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseOwnHold(1L, "alice");

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void releaseOwnHold_whenNotHeldAtAll_isSilentNoOp() {
        Seat seat = seat(SeatStatus.AVAILABLE, null, null);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.releaseOwnHold(1L, "alice");

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void confirmHold_whenHeldAndNotExpired_marksBookedAndClearsOwner() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().plusSeconds(300), ALICE);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.confirmHold(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        assertThat(seat.getHeldUntil()).isNull();
        assertThat(seat.getHeldBy()).isNull();
    }

    @Test
    void confirmHold_whenHeldWithNoExpirationSet_marksBooked() {
        // Defensive branch: a HELD seat with a null heldUntil is never
        // treated as expired, since isExpiredHold short-circuits on the
        // null check before comparing instants.
        Seat seat = seat(SeatStatus.HELD, null, ALICE);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        seatHoldService.confirmHold(1L);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void confirmHold_whenHoldExpired_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.HELD, Instant.now().minusSeconds(1), ALICE);
        given(seatRepository.findByIdForUpdate(1L)).willReturn(Optional.of(seat));

        assertThatThrownBy(() -> seatHoldService.confirmHold(1L))
                .isInstanceOf(SeatUnavailableException.class);
    }

    @Test
    void confirmHold_whenNotHeld_throwsSeatUnavailableException() {
        Seat seat = seat(SeatStatus.AVAILABLE, null, null);
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
