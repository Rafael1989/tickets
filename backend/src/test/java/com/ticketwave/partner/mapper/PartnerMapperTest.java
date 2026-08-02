package com.ticketwave.partner.mapper;

import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PartnerMapperTest {

    private final PartnerMapper mapper = new PartnerMapperImpl();

    @Test
    void toEntity_mapsRequestFieldsAndIgnoresServerControlledOnes() {
        PartnerRequest request = new PartnerRequest("Acme Transit", "ops@acme.example", new BigDecimal("0.1000"));

        Partner partner = mapper.toEntity(request);

        assertThat(partner.getName()).isEqualTo("Acme Transit");
        assertThat(partner.getContactEmail()).isEqualTo("ops@acme.example");
        assertThat(partner.getCommissionRate()).isEqualByComparingTo("0.1000");
        assertThat(partner.getId()).isNull();
        assertThat(partner.getStatus()).isNull();
        assertThat(partner.getCreatedAt()).isNull();
    }

    @Test
    void toResponse_mapsEveryField() {
        Instant now = Instant.parse("2026-08-01T00:00:00Z");
        Partner partner = Partner.builder().id(9L).name("Acme Transit").contactEmail("ops@acme.example")
                .status(PartnerStatus.ACTIVE).commissionRate(new BigDecimal("0.1000")).createdAt(now).build();

        PartnerResponse response = mapper.toResponse(partner);

        assertThat(response).isEqualTo(new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.ACTIVE, new BigDecimal("0.1000"), now));
    }
}
