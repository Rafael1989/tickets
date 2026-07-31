# TicketWave — Repo Guidelines

TicketWave is a ticket booking platform (flights, buses, trains, events) covering
search, seat selection, dynamic pricing, booking, payment, and refunds, with
role-based access (customer, operator, support, admin). Full spec: `genai.txt`.

Stack: Spring Boot 4.0.6 (Java) + PostgreSQL + Liquibase + MapStruct + JWT + OpenAPI
on the backend, Angular on the frontend.

Core entities: User, Passenger, Route, Schedule, Seat, Booking, BookingItem,
Payment, Refund.

## Backend layering

Strict one-way dependency flow, no layer-skipping:

```
Controller -> Service (interface + impl) -> Repository -> Entity
                 |
                 v
         DTO <-> Entity via MapStruct mapper
```

- **Controllers**: HTTP concerns only (routing, status codes, `@Valid` request
  binding). No business logic, no direct repository access.
- **Services**: all business logic and transaction boundaries
  (`@Transactional` lives here, never on controllers or repositories). Depend
  on a service interface where a component has meaningful behavior worth
  mocking in tests (e.g. `BookingService`); trivial CRUD-only services can skip
  the interface.
- **Repositories**: Spring Data JPA interfaces only. No business logic, no
  DTO mapping.
- **Entities**: persistence model only. Never returned from a controller and
  never accepted as a request body — always cross the API boundary as a DTO.
- **Mappers**: MapStruct interfaces (`@Mapper(componentModel = "spring")`)
  convert Entity <-> DTO. Mapping logic doesn't belong in services or entities.

## Naming conventions

**Java packages** (feature-based, not layer-based, under
`com.ticketwave.<feature>`):
```
com.ticketwave.booking.controller
com.ticketwave.booking.service
com.ticketwave.booking.repository
com.ticketwave.booking.entity
com.ticketwave.booking.dto
com.ticketwave.booking.mapper
```

**Java types**
- Entities: singular noun, no suffix — `Booking`, `Seat`, `Payment`.
- DTOs: intent-suffixed, never reuse one DTO for both directions —
  `BookingRequest` / `BookingResponse`, `CreateBookingRequest` when a
  resource has multiple write shapes.
- Services: `XxxService` interface + `XxxServiceImpl` implementation.
- Repositories: `XxxRepository extends JpaRepository<Xxx, Long>`.
- Controllers: `XxxController`, REST resource named after the plural noun
  in the path (`/api/bookings`).
- Custom exceptions: `XxxException`, suffixed by what went wrong —
  `SeatUnavailableException`, `BookingNotFoundException`.
- Mappers: `XxxMapper`.
- Liquibase changelog files: `YYYY-MM-DD-<seq>-<description>.xml` (or
  `.yaml`), one logical change per changeset, never edit a changeset that has
  already shipped — add a new one.

**Angular**
- One concern per file: `feature-name.component.ts/.html/.scss`,
  `feature-name.service.ts`, `feature-name.model.ts`, `feature-name.guard.ts`.
- Selectors prefixed `tw-` (e.g. `tw-seat-map`).
- Feature modules/standalone components grouped under `src/app/features/<feature>/`;
  cross-cutting code (interceptors, guards, shared models) under
  `src/app/core/` and `src/app/shared/`.
- Services that call the backend are named after the resource, not the verb:
  `booking.service.ts`, not `get-booking.service.ts`.

## Error handling

- Backend: a single `@RestControllerAdvice` (`GlobalExceptionHandler`) maps
  domain exceptions to HTTP status and a consistent error body:
  ```json
  { "status": 404, "error": "BOOKING_NOT_FOUND", "message": "...", "timestamp": "..." }
  ```
  Never let a raw exception/stack trace reach the client. Never swallow an
  exception silently — log it or let it propagate to the advice.
- Throw specific exceptions from services (`SeatUnavailableException`), not
  generic `RuntimeException`. Validate request DTOs with Bean Validation
  (`@NotNull`, `@Positive`, etc.) at the controller boundary rather than
  hand-rolled null checks in services.
- Money/amount fields: `BigDecimal`, never `float`/`double`.
- Frontend: a single `HttpInterceptor` handles error responses centrally
  (auth redirects on 401, toast/notification on 4xx/5xx). Components don't
  each implement their own HTTP error branching — they subscribe to
  already-normalized results/errors from the service layer.

## Testing standards

- Target ≥80% line coverage on service and controller layers; 100% on
  pricing, seat-hold, and refund-proration logic specifically, since these
  are the modules where a silent bug has direct financial impact.
- Unit tests (JUnit 5 + Mockito) for services: mock the repository/dependency
  layer, assert business rules (hold expiration, pricing modifiers, refund
  calculation, PNR uniqueness).
- Integration tests (`@SpringBootTest` + Testcontainers against real
  PostgreSQL, not H2) for repository queries and full booking/payment flows,
  since Liquibase-managed schema and JSON/enum column behavior can diverge
  from an in-memory DB.
- Controller tests (`@WebMvcTest` + MockMvc) verify request validation, status
  codes, and error-body shape — not business logic.
- Test naming: `methodName_condition_expectedResult` (e.g.
  `holdSeat_whenAlreadyHeld_throwsSeatUnavailableException`).
- Every bug fix ships with a regression test reproducing the original failure.
- Frontend: Jasmine/Karma (or Jest, whichever the project scaffold uses) for
  component and service unit tests; cover seat-hold countdown, checkout
  validation, and error-state rendering as the highest-value cases.

## API conventions

- Endpoints and versioning follow the paths in `genai.txt` (`/api/bookings`,
  `/api/schedules/{id}/seats`, etc.); don't introduce a parallel naming
  scheme for new resources.
- All endpoints documented via OpenAPI/springdoc annotations as they're
  written, not retrofitted later.
- Idempotency keys required on payment-initiating endpoints
  (`POST /api/bookings/{id}/payments`) to prevent double-charging on retry.
- JWT auth: role checks (`customer`, `operator`, `support`, `admin`) enforced
  via method security (`@PreAuthorize`) on the service or controller method,
  not scattered `if` checks against the principal.
