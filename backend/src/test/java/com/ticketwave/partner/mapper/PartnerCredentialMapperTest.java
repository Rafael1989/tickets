package com.ticketwave.partner.mapper;

import com.ticketwave.partner.dto.PartnerCredentialResponse;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerApiCredential;
import com.ticketwave.partner.entity.PartnerCredentialStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerCredentialMapperTest {

    private final PartnerCredentialMapper mapper = new PartnerCredentialMapperImpl();

    @Test
    void toResponse_flattensPartnerIdAndNeverExposesTheSecretHash() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        PartnerApiCredential credential = PartnerApiCredential.builder().id(1L)
                .partner(Partner.builder().id(9L).build())
                .clientId("pk_abc").clientSecretHash("bcrypt-hash")
                .status(PartnerCredentialStatus.ACTIVE).createdAt(now).build();

        PartnerCredentialResponse response = mapper.toResponse(credential);

        assertThat(response).isEqualTo(new PartnerCredentialResponse(1L, 9L, "pk_abc", PartnerCredentialStatus.ACTIVE, now, null, null));
    }
}
