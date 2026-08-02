package com.ticketwave.partner.mapper;

import com.ticketwave.partner.dto.PartnerWebhookResponse;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerWebhook;
import com.ticketwave.partner.entity.WebhookStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerWebhookMapperTest {

    private final PartnerWebhookMapper mapper = new PartnerWebhookMapperImpl();

    @Test
    void toResponse_flattensPartnerIdAndNeverExposesTheSecret() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        PartnerWebhook webhook = PartnerWebhook.builder().id(1L)
                .partner(Partner.builder().id(9L).build())
                .url("https://partner.example/hook").secret("signing-secret")
                .eventType("BOOKING_CANCELLED").status(WebhookStatus.ACTIVE).createdAt(now).build();

        PartnerWebhookResponse response = mapper.toResponse(webhook);

        assertThat(response).isEqualTo(new PartnerWebhookResponse(1L, 9L, "https://partner.example/hook", "BOOKING_CANCELLED", WebhookStatus.ACTIVE, now));
    }
}
