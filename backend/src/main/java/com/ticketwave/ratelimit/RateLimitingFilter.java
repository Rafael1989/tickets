package com.ticketwave.ratelimit;

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
 * Throttles only the configured public paths, by client IP. Deliberately a
 * plain (non-@Component) Filter, wired up through exactly one
 * FilterRegistrationBean in RateLimitConfig — making it a @Component as well
 * would get it auto-registered a second time by Spring Boot, which is
 * exactly the bug that broke JwtAuthenticationFilter earlier (see
 * SecurityConfig's jwtAuthenticationFilterRegistration for the full story).
 *
 * getRemoteAddr() is the real client IP even behind a reverse proxy: see
 * application.yml's server.forward-headers-strategy, which has embedded
 * Tomcat rewrite it from X-Forwarded-For (only trusting that header from a
 * recognized internal-proxy peer, so a direct client can't spoof it).
 */
public class RateLimitingFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final List<String> rateLimitedPatterns;
    private final long windowSeconds;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitingFilter(RateLimiter rateLimiter, List<String> rateLimitedPatterns, long windowSeconds) {
        this.rateLimiter = rateLimiter;
        this.rateLimitedPatterns = rateLimitedPatterns;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (matchesRateLimitedPath(request) && !rateLimiter.tryConsume(request.getRemoteAddr())) {
            respondTooManyRequests(response);
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }

    private boolean matchesRateLimitedPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        String path = (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;

        return rateLimitedPatterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
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
