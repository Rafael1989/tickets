package com.ticketwave.config;

import com.ticketwave.ratelimit.RateLimiter;
import com.ticketwave.ratelimit.RateLimitingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Clock;

@Configuration
public class RateLimitConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Runs before Spring Security's own filter chain, so a request that's
     * already over its limit is rejected immediately rather than spending
     * cycles on JWT parsing/authorization first.
     */
    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(
            RateLimiter rateLimiter,
            RateLimitProperties properties
    ) {
        RateLimitingFilter filter = new RateLimitingFilter(rateLimiter, PublicEndpoints.all(), properties.windowSeconds());

        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
