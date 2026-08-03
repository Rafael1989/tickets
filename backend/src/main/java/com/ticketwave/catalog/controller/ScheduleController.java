package com.ticketwave.catalog.controller;

import com.ticketwave.catalog.dto.ScheduleSearchResult;
import com.ticketwave.catalog.dto.SeatResponse;
import com.ticketwave.catalog.exception.SeatNotFoundException;
import com.ticketwave.catalog.service.ScheduleSearchService;
import com.ticketwave.catalog.service.SeatHoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@Tag(name = "Schedules", description = "GET endpoints are public guest browsing, rate-limited. The hold/release endpoints under /seats/{id}/hold require a bearer JWT.")
public class ScheduleController {

    private static final String ANONYMOUS_PRINCIPAL = "anonymousUser";

    private final ScheduleSearchService scheduleSearchService;
    private final SeatHoldService seatHoldService;

    public ScheduleController(ScheduleSearchService scheduleSearchService, SeatHoldService seatHoldService) {
        this.scheduleSearchService = scheduleSearchService;
        this.seatHoldService = seatHoldService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get schedule details", security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Schedule found"),
            @ApiResponse(responseCode = "404", description = "No such schedule"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<ScheduleSearchResult> getSchedule(@PathVariable("id") Long scheduleId) {
        return ResponseEntity.ok(scheduleSearchService.getScheduleDetails(scheduleId));
    }

    @GetMapping("/{id}/seats")
    @Operation(
            summary = "Get seat availability for a schedule",
            description = "Returns every seat on the schedule, including HELD/BOOKED ones, so a client can render a full seat map rather than just the bookable subset. Each seat's estimatedFare mirrors exactly what booking creation would charge. heldByMe is only ever true when called with a bearer token belonging to the seat's current holder.",
            security = {}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seat list"),
            @ApiResponse(responseCode = "404", description = "No such schedule"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<List<SeatResponse>> getSeats(
            Authentication authentication,
            @PathVariable("id") Long scheduleId
    ) {
        return ResponseEntity.ok(scheduleSearchService.getSeatsForSchedule(scheduleId, callerUsername(authentication)));
    }

    @PostMapping("/{scheduleId}/seats/{seatId}/hold")
    @Operation(
            summary = "Hold a seat for the authenticated caller ahead of booking",
            description = "Reserves the seat for the configured TTL, refreshed on every call — including a later booking-creation call over the same seat by its own holder, which re-affirms rather than re-holds. Requires authentication: guests keep client-side-only seat selection until they log in."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Seat held (or the caller's existing hold was refreshed)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "No such seat"),
            @ApiResponse(responseCode = "409", description = "Seat is booked, or held by someone else")
    })
    public ResponseEntity<SeatResponse> holdSeat(
            Authentication authentication,
            @PathVariable("scheduleId") Long scheduleId,
            @PathVariable("seatId") Long seatId
    ) {
        seatHoldService.holdSeatForUsername(seatId, authentication.getName());
        // Re-fetched (rather than mapped directly off the just-held Seat) so
        // the response carries the same real estimatedFare/heldByMe the seat
        // map itself shows — the entity coming back from SeatHoldService has
        // neither, since computing them needs the Schedule and caller
        // identity that only ScheduleSearchService's enrichment has.
        return ResponseEntity.ok(findSeat(scheduleId, seatId, authentication.getName()));
    }

    @DeleteMapping("/{scheduleId}/seats/{seatId}/hold")
    @Operation(
            summary = "Release the authenticated caller's own hold on a seat",
            description = "Idempotent no-op if the seat isn't currently held by the caller — never reveals whether it's available, booked, or held by someone else."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Released (or was already not held by the caller)"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "No such seat")
    })
    public ResponseEntity<Void> releaseSeat(
            Authentication authentication,
            @PathVariable("scheduleId") Long scheduleId,
            @PathVariable("seatId") Long seatId
    ) {
        seatHoldService.releaseOwnHold(seatId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private SeatResponse findSeat(Long scheduleId, Long seatId, String username) {
        return scheduleSearchService.getSeatsForSchedule(scheduleId, username).stream()
                .filter(seat -> seat.id().equals(seatId))
                .findFirst()
                .orElseThrow(() -> new SeatNotFoundException(seatId));
    }

    /**
     * Guest browsing is supported, so this collapses "no identified caller"
     * into null rather than rejecting the request.
     *
     * Note for coverage readers: only the null check is reachable through
     * Spring MVC. A bare Authentication parameter is resolved from
     * HttpServletRequest#getUserPrincipal(), and
     * SecurityContextHolderAwareRequestWrapper already returns null for an
     * anonymous authentication — so the anonymous and not-authenticated arms
     * below never execute on this path, and are kept as defence in depth
     * against that resolution ever changing. See docs/testing.md.
     */
    private static String callerUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || ANONYMOUS_PRINCIPAL.equals(authentication.getName())) {
            return null;
        }
        return authentication.getName();
    }
}
