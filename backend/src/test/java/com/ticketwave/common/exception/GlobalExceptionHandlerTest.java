package com.ticketwave.common.exception;

import com.ticketwave.booking.exception.BookingNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDomainException_mapsStatusAndErrorCodeFromTheException() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new BookingNotFoundException(500L));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().error()).isEqualTo("BOOKING_NOT_FOUND");
    }

    @Test
    void handleValidationException_returns400WithFieldErrorDetails() {
        FieldError fieldError = new FieldError("request", "username", "must not be blank");
        BindingResult bindingResult = mock(BindingResult.class);
        given(bindingResult.getFieldErrors()).willReturn(List.of(fieldError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        given(ex.getBindingResult()).willReturn(bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().details()).containsExactly("username: must not be blank");
    }

    @Test
    void handleAccessDenied_returns403() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().error()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void handleUnexpectedException_returns500WithoutLeakingTheOriginalMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("boom");
    }
}
