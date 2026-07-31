package com.ticketwave.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void ticketwaveOpenApi_configuresInfoAndBearerScheme() {
        OpenAPI api = new OpenApiConfig().ticketwaveOpenApi();

        assertThat(api.getInfo().getTitle()).isEqualTo("TicketWave API");
        assertThat(api.getInfo().getVersion()).isEqualTo("v1");

        SecurityScheme bearerAuth = api.getComponents().getSecuritySchemes().get("bearerAuth");
        assertThat(bearerAuth).isNotNull();
        assertThat(bearerAuth.getType()).isEqualTo(SecurityScheme.Type.HTTP);
        assertThat(bearerAuth.getScheme()).isEqualTo("bearer");
        assertThat(bearerAuth.getBearerFormat()).isEqualTo("JWT");

        assertThat(api.getSecurity()).isNotEmpty();
        assertThat(api.getSecurity().get(0)).containsKey("bearerAuth");
    }
}
