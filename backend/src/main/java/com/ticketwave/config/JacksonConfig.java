package com.ticketwave.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 4's own Jackson autoconfiguration only produces the new
 * Jackson 3.x tools.jackson.databind.json.JsonMapper — it never provides a
 * bean of the legacy com.fasterxml.jackson.databind.ObjectMapper type, even
 * though that type is still on the classpath (as a transitive dependency of
 * jjwt-jackson). PartnerWebhookDeliveryService needs exactly that legacy
 * type to serialize webhook payloads, so without this bean the application
 * cannot start at all — findAndRegisterModules() picks up JSR-310
 * (Instant/LocalDate) support automatically, needed since webhook payloads
 * (e.g. BookingCancelledWebhookPayload) carry Instant fields.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
