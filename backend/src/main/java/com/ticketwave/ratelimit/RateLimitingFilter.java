package com.ticketwave.ratelimit;

import com.ticketwave.auth.JwtService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Throttles the configured public paths by client IP, and the configured
 * partner-API paths by the caller's partner (its PARTNER_API token's
 * clientId subject, falling back to IP if no valid such token is present —
 * e.g. an unauthenticated request that's about to get a 401 from Spring
 * Security anyway). Deliberately a plain (non-@Component) Filter, wired up
 * through exactly one FilterRegistrationBean in RateLimitConfig — making it
 * a @Component as well would get it auto-registered a second time by Spring
 * Boot, which is exactly the bug that broke JwtAuthenticationFilter earlier
 * (see SecurityConfig's jwtAuthenticationFilterRegistration for the full
 * story).
 *
 * getRemoteAddr() is the real client IP even behind a reverse proxy: see
 * application.yml's server.forward-headers-strategy, which has embedded
 * Tomcat rewrite it from X-Forwarded-For (only trusting that header from a
 * recognized internal-proxy peer, so a direct client can't spoof it).
 */
public class RateLimitingFilter implements Filter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PARTNER_API_ROLE = "PARTNER_API";

    private final RateLimiter rateLimiter;
    private final List<String> ipKeyedPatterns;
    private final List<String> partnerKeyedPatterns;
    private final JwtService jwtService;
    private final long windowSeconds;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitingFilter(
            RateLimiter rateLimiter,
            List<String> ipKeyedPatterns,
            List<String> partnerKeyedPatterns,
            JwtService jwtService,
            long windowSeconds
    ) {
        this.rateLimiter = rateLimiter;
        this.ipKeyedPatterns = ipKeyedPatterns;
        this.partnerKeyedPatterns = partnerKeyedPatterns;
        this.jwtService = jwtService;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = requestPath(request);

        boolean partnerKeyed = matches(partnerKeyedPatterns, path);
        boolean ipKeyed = !partnerKeyed && matches(ipKeyedPatterns, path);

        if ((partnerKeyed || ipKeyed) && !rateLimiter.tryConsume(rateLimitKey(request, partnerKeyed))) {
            respondTooManyRequests(response);
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private String requestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }

    private boolean matches(List<String> patterns, String path) {
        return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String rateLimitKey(HttpServletRequest request, boolean partnerKeyed) {
        if (partnerKeyed) {
            String partnerKey = partnerRateLimitKey(request);
            if (partnerKey != null) {
                return partnerKey;
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * Only ever consulted for paths in partnerKeyedPatterns — parses the
     * bearer token directly (rather than reading SecurityContextHolder)
     * because this filter deliberately runs before Spring Security's chain,
     * so no authentication has been established yet at this point.
     */
    private String partnerRateLimitKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!jwtService.isValid(token) || !jwtService.extractRoles(token).contains(PARTNER_API_ROLE)) {
            return null;
        }
        return "partner:" + jwtService.extractSubject(token);
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.setHeader("Retry-After", String.valueOf(windowSeconds));
        response.getWriter().write(
                "{\"status\":429,\"error\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too many requests, please try again later.\",\"timestamp\":\""
                        + Instant.now() + "\"}");
    }
}
