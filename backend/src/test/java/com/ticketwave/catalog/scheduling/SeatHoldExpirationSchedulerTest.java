package com.ticketwave.catalog.scheduling;

import com.ticketwave.catalog.service.SeatHoldService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatHoldExpirationSchedulerTest {

    @Mock
    private SeatHoldService seatHoldService;

    @Test
    void releaseExpiredHolds_whenSomeReleased_delegatesToSeatHoldService() {
        given(seatHoldService.releaseExpiredHolds()).willReturn(3);

        new SeatHoldExpirationScheduler(seatHoldService).releaseExpiredHolds();

        verify(seatHoldService).releaseExpiredHolds();
    }

    @Test
    void releaseExpiredHolds_whenNoneReleased_stillDelegatesWithoutError() {
        given(seatHoldService.releaseExpiredHolds()).willReturn(0);

        new SeatHoldExpirationScheduler(seatHoldService).releaseExpiredHolds();

        verify(seatHoldService).releaseExpiredHolds();
    }
}
