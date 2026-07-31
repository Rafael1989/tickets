package com.ticketwave.devseed;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.function.Supplier;

/**
 * The seeder runs outside any HTTP request, so there's no Authentication in
 * SecurityContextHolder for the @PreAuthorize-guarded methods on
 * PaymentService/RefundService to evaluate. This stands one up temporarily —
 * the same pattern PaymentFlowIT/RefundFlowIT use to exercise those methods
 * directly — and always restores whatever was there before, even on failure.
 */
final class SeedAuthContext {

    private SeedAuthContext() {
    }

    static <T> T runAs(String username, String role, Supplier<T> action) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
