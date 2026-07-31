package com.ticketwave.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI ticketwaveOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketWave API")
                        .description("""
                                Search, booking, payment, and refund API for the TicketWave ticket \
                                booking system. Search and schedule browsing are public; everything \
                                else requires a bearer JWT obtained from /api/login. Public endpoints \
                                are rate-limited per client IP (see the ticketwave.rate-limit.* \
                                configuration); exceeding the limit returns 429 with a Retry-After header.""")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .name(BEARER_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
