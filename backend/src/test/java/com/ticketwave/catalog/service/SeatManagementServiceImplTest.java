package com.ticketwave.catalog.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.catalog.dto.SeatRequest;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.dto.SeatUpdateRequest;
import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.exception.ScheduleNotFoundException;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.exception.SeatUnavailableException;
import com.ticketwave.catalog.mapper.SeatMapper;
import com.ticketwave.catalog.repository.ScheduleRepository;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.catalog.security.TenantScope;
import com.ticketwave.user.entity.User;
import com.ticketwave.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SeatManagementServiceImplTest {

    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SeatMapper seatMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private TenantScope tenantScope;

    @InjectMocks
    private SeatManagementServiceImpl seatManagementService;

    /**
     * Reproduces the pre-multi-tenant "exact same username" ownership check
     * through the new UserRepository/TenantScope collaborators, so every
     * existing test below keeps its original username-based semantics
     * without needing to stub these two on a per-test basis.
     */
    @BeforeEach
    void stubTenantResolutionByUsername() {
        org.mockito.Mockito.lenient().when(userRepository.findByUsername(any()))
                .thenAnswer(inv -> Optional.of(User.builder().username(inv.getArgument(0)).build()));
        org.mockito.Mockito.lenient().when(tenantScope.isSameTenant(any(), any()))
                .thenAnswer(inv -> inv.getArgument(0, User.class).getUsername().equals(inv.getArgument(1, User.class).getUsername()));
    }

    private static Schedule scheduleOwnedBy(long id, String operatorUsername) {
        Route route = Route.builder().operator(User.builder().username(operatorUsername).build()).build();
        return Schedule.builder().id(id).route(route).build();
    }

    @Test
    void addSeat_whenScheduleOwnedByOperator_savesAndDefaultsStatusWhenAbsent() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        SeatRequest request = new SeatRequest(1L, "1A", "economy", null, new BigDecimal("1.000"));
        Seat mapped = Seat.builder().schedule(schedule).seatNumber("1A").build();
        Seat saved = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.AVAILABLE).build();
        SeatResponse response = new SeatResponse(5L, 1L, "1A", "economy", SeatStatus.AVAILABLE, new BigDecimal("1.000"), null, null, false);

        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        given(seatMapper.toEntity(request, schedule)).willReturn(mapped);
        given(seatRepository.save(mapped)).willReturn(saved);
        given(seatMapper.toResponse(saved)).willReturn(response);

        SeatResponse result = seatManagementService.addSeat("operator1", request);

        assertThat(result).isEqualTo(response);
        assertThat(mapped.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void addSeat_whenScheduleBelongsToDifferentOperator_throwsScheduleNotFoundException() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        given(scheduleRepository.findById(1L)).willReturn(Optional.of(schedule));
        SeatRequest request = new SeatRequest(1L, "1A", "economy", null, new BigDecimal("1.000"));

        assertThatThrownBy(() -> seatManagementService.addSeat("mallory", request))
                .isInstanceOf(ScheduleNotFoundException.class);
    }

    @Test
    void updateSeat_whenAvailable_updatesStatusAndPriceModifierAndAudits() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        Seat seat = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.AVAILABLE)
                .priceModifier(BigDecimal.ONE).build();
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.BLOCKED, new BigDecimal("1.500"));
        given(seatRepository.findByIdForUpdate(5L)).willReturn(Optional.of(seat));
        given(seatMapper.toResponse(seat)).willReturn(
                new SeatResponse(5L, 1L, null, null, SeatStatus.BLOCKED, new BigDecimal("1.500"), null, null, false));

        SeatResponse result = seatManagementService.updateSeat("operator1", 5L, request);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BLOCKED);
        assertThat(seat.getPriceModifier()).isEqualByComparingTo("1.500");
        assertThat(result.status()).isEqualTo(SeatStatus.BLOCKED);
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void updateSeat_clearsAnyStaleHoldMetadataOnTransition() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        User holder = User.builder().id(2L).username("alice").build();
        Seat seat = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.AVAILABLE)
                .priceModifier(BigDecimal.ONE).heldBy(holder).heldUntil(Instant.now().minusSeconds(60)).build();
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.RESERVED_OPERATOR, BigDecimal.ONE);
        given(seatRepository.findByIdForUpdate(5L)).willReturn(Optional.of(seat));
        given(seatMapper.toResponse(seat)).willReturn(
                new SeatResponse(5L, 1L, null, null, SeatStatus.RESERVED_OPERATOR, BigDecimal.ONE, null, null, false));

        seatManagementService.updateSeat("operator1", 5L, request);

        assertThat(seat.getHeldBy()).isNull();
        assertThat(seat.getHeldUntil()).isNull();
    }

    @Test
    void updateSeat_whenBooked_throwsSeatUnavailableExceptionAndNeverMutates() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        Seat seat = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.BOOKED)
                .priceModifier(BigDecimal.ONE).build();
        given(seatRepository.findByIdForUpdate(5L)).willReturn(Optional.of(seat));
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.BLOCKED, BigDecimal.ONE);

        assertThatThrownBy(() -> seatManagementService.updateSeat("operator1", 5L, request))
                .isInstanceOf(SeatUnavailableException.class);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.BOOKED);
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void updateSeat_whenActivelyHeldByCustomer_throwsSeatUnavailableExceptionAndNeverMutates() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        Seat seat = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.HELD)
                .priceModifier(BigDecimal.ONE).heldUntil(Instant.now().plusSeconds(120)).build();
        given(seatRepository.findByIdForUpdate(5L)).willReturn(Optional.of(seat));
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.BLOCKED, BigDecimal.ONE);

        assertThatThrownBy(() -> seatManagementService.updateSeat("operator1", 5L, request))
                .isInstanceOf(SeatUnavailableException.class);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void updateSeat_whenHeldButExpired_isAllowed() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        Seat seat = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.HELD)
                .priceModifier(BigDecimal.ONE).heldUntil(Instant.now().minusSeconds(60)).build();
        given(seatRepository.findByIdForUpdate(5L)).willReturn(Optional.of(seat));
        given(seatMapper.toResponse(seat)).willReturn(
                new SeatResponse(5L, 1L, null, null, SeatStatus.AVAILABLE, BigDecimal.ONE, null, null, false));
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.AVAILABLE, BigDecimal.ONE);

        seatManagementService.updateSeat("operator1", 5L, request);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    void updateSeat_whenSeatBelongsToDifferentOperator_throwsSeatNotFoundException() {
        Schedule schedule = scheduleOwnedBy(1L, "operator1");
        Seat seat = Seat.builder().id(5L).schedule(schedule).status(SeatStatus.AVAILABLE).build();
        given(seatRepository.findByIdForUpdate(5L)).willReturn(Optional.of(seat));
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.BLOCKED, BigDecimal.ONE);

        assertThatThrownBy(() -> seatManagementService.updateSeat("mallory", 5L, request))
                .isInstanceOf(SeatNotFoundException.class);
    }

    @Test
    void updateSeat_whenMissing_throwsSeatNotFoundException() {
        given(seatRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());
        SeatUpdateRequest request = new SeatUpdateRequest(SeatStatus.BLOCKED, BigDecimal.ONE);

        assertThatThrownBy(() -> seatManagementService.updateSeat("operator1", 99L, request))
                .isInstanceOf(SeatNotFoundException.class);
    }
}
