package com.ticketwave.partner.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerStatus;
import com.ticketwave.partner.exception.DuplicatePartnerException;
import com.ticketwave.partner.exception.PartnerNotFoundException;
import com.ticketwave.partner.mapper.PartnerMapper;
import com.ticketwave.partner.repository.PartnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartnerServiceImplTest {

    @Mock
    private PartnerRepository partnerRepository;
    @Mock
    private PartnerMapper partnerMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PartnerServiceImpl partnerService;

    private static PartnerRequest request() {
        return new PartnerRequest("Acme Transit", "ops@acme.example", new BigDecimal("0.1000"));
    }

    @Test
    void createPartner_whenNameAvailable_savesAsPendingAndAudits() {
        PartnerRequest request = request();
        Partner mapped = Partner.builder().name("Acme Transit").contactEmail("ops@acme.example").build();
        Partner saved = Partner.builder().id(9L).name("Acme Transit").status(PartnerStatus.PENDING).createdAt(Instant.now()).build();

        given(partnerRepository.existsByName("Acme Transit")).willReturn(false);
        given(partnerMapper.toEntity(request)).willReturn(mapped);
        given(partnerRepository.save(mapped)).willReturn(saved);
        given(partnerMapper.toResponse(saved)).willReturn(
                new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.PENDING, request.commissionRate(), saved.getCreatedAt()));

        PartnerResponse result = partnerService.createPartner("admin1", request);

        assertThat(mapped.getStatus()).isEqualTo(PartnerStatus.PENDING);
        assertThat(result.status()).isEqualTo(PartnerStatus.PENDING);
        verify(auditService).record("admin1", "PARTNER_CREATED", "PARTNER", 9L, "name=Acme Transit");
    }

    @Test
    void createPartner_whenNameTaken_throwsDuplicatePartnerExceptionAndNeverSaves() {
        given(partnerRepository.existsByName("Acme Transit")).willReturn(true);

        assertThatThrownBy(() -> partnerService.createPartner("admin1", request()))
                .isInstanceOf(DuplicatePartnerException.class);

        verify(partnerRepository, never()).save(any());
    }

    @Test
    void getPartner_whenFound_returnsMappedResponse() {
        Partner partner = Partner.builder().id(9L).name("Acme Transit").build();
        given(partnerRepository.findById(9L)).willReturn(Optional.of(partner));
        PartnerResponse response = new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.ACTIVE, BigDecimal.TEN, Instant.now());
        given(partnerMapper.toResponse(partner)).willReturn(response);

        assertThat(partnerService.getPartner(9L)).isEqualTo(response);
    }

    @Test
    void getPartner_whenMissing_throwsPartnerNotFoundException() {
        given(partnerRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.getPartner(99L))
                .isInstanceOf(PartnerNotFoundException.class);
    }

    @Test
    void listPartners_returnsEveryPartnerMappedToResponse() {
        Partner partner = Partner.builder().id(9L).name("Acme Transit").build();
        given(partnerRepository.findAll()).willReturn(List.of(partner));
        PartnerResponse response = new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.ACTIVE, BigDecimal.TEN, Instant.now());
        given(partnerMapper.toResponse(partner)).willReturn(response);

        assertThat(partnerService.listPartners()).containsExactly(response);
    }

    @Test
    void updateStatus_whenFound_changesStatusAndAudits() {
        Partner partner = Partner.builder().id(9L).name("Acme Transit").status(PartnerStatus.PENDING).build();
        given(partnerRepository.findById(9L)).willReturn(Optional.of(partner));
        given(partnerMapper.toResponse(partner)).willReturn(
                new PartnerResponse(9L, "Acme Transit", "ops@acme.example", PartnerStatus.ACTIVE, BigDecimal.TEN, Instant.now()));

        PartnerResponse result = partnerService.updateStatus("admin1", 9L, PartnerStatus.ACTIVE);

        assertThat(partner.getStatus()).isEqualTo(PartnerStatus.ACTIVE);
        assertThat(result.status()).isEqualTo(PartnerStatus.ACTIVE);
        ArgumentCaptor<String> detailsCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditService).record(any(), any(), any(), any(), detailsCaptor.capture());
        assertThat(detailsCaptor.getValue()).isEqualTo("PENDING -> ACTIVE");
    }

    @Test
    void updateStatus_whenMissing_throwsPartnerNotFoundException() {
        given(partnerRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.updateStatus("admin1", 99L, PartnerStatus.ACTIVE))
                .isInstanceOf(PartnerNotFoundException.class);
    }
}
