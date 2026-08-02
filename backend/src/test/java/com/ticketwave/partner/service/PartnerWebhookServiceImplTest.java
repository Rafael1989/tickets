package com.ticketwave.partner.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.partner.dto.PartnerWebhookIssuedResponse;
import com.ticketwave.partner.dto.PartnerWebhookRequest;
import com.ticketwave.partner.dto.PartnerWebhookResponse;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerWebhook;
import com.ticketwave.partner.entity.WebhookStatus;
import com.ticketwave.partner.exception.PartnerNotFoundException;
import com.ticketwave.partner.exception.PartnerWebhookNotFoundException;
import com.ticketwave.partner.mapper.PartnerWebhookMapper;
import com.ticketwave.partner.repository.PartnerRepository;
import com.ticketwave.partner.repository.PartnerWebhookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartnerWebhookServiceImplTest {

    @Mock
    private PartnerWebhookRepository webhookRepository;
    @Mock
    private PartnerRepository partnerRepository;
    @Mock
    private PartnerWebhookMapper webhookMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PartnerWebhookServiceImpl webhookService;

    @Test
    void registerWebhook_whenPartnerFound_generatesSecretAndPersists() {
        Partner partner = Partner.builder().id(9L).build();
        PartnerWebhookRequest request = new PartnerWebhookRequest("https://partner.example/webhooks", "BOOKING_CANCELLED");
        given(partnerRepository.findById(9L)).willReturn(Optional.of(partner));
        given(webhookRepository.save(org.mockito.ArgumentMatchers.any())).willAnswer(inv -> {
            PartnerWebhook w = inv.getArgument(0);
            w.setId(1L);
            w.setCreatedAt(Instant.now());
            return w;
        });

        PartnerWebhookIssuedResponse response = webhookService.registerWebhook("admin1", 9L, request);

        assertThat(response.partnerId()).isEqualTo(9L);
        assertThat(response.url()).isEqualTo("https://partner.example/webhooks");
        assertThat(response.secret()).isNotBlank();

        ArgumentCaptor<PartnerWebhook> captor = ArgumentCaptor.forClass(PartnerWebhook.class);
        verify(webhookRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(WebhookStatus.ACTIVE);
        verify(auditService).record("admin1", "PARTNER_WEBHOOK_REGISTERED", "PARTNER_WEBHOOK", 1L,
                "partnerId=9 eventType=BOOKING_CANCELLED");
    }

    @Test
    void registerWebhook_whenPartnerMissing_throwsPartnerNotFoundException() {
        given(partnerRepository.findById(99L)).willReturn(Optional.empty());
        PartnerWebhookRequest request = new PartnerWebhookRequest("https://partner.example/webhooks", "BOOKING_CANCELLED");

        assertThatThrownBy(() -> webhookService.registerWebhook("admin1", 99L, request))
                .isInstanceOf(PartnerNotFoundException.class);
    }

    @Test
    void listWebhooks_returnsEveryWebhookMappedToResponse() {
        PartnerWebhook webhook = PartnerWebhook.builder().id(1L).partner(Partner.builder().id(9L).build()).build();
        given(webhookRepository.findByPartnerId(9L)).willReturn(List.of(webhook));
        PartnerWebhookResponse response = new PartnerWebhookResponse(1L, 9L, "https://partner.example", "BOOKING_CANCELLED", WebhookStatus.ACTIVE, Instant.now());
        given(webhookMapper.toResponse(webhook)).willReturn(response);

        assertThat(webhookService.listWebhooks(9L)).containsExactly(response);
    }

    @Test
    void updateStatus_whenFound_changesStatusAndAudits() {
        PartnerWebhook webhook = PartnerWebhook.builder().id(1L).status(WebhookStatus.ACTIVE).build();
        given(webhookRepository.findById(1L)).willReturn(Optional.of(webhook));
        given(webhookMapper.toResponse(webhook)).willReturn(
                new PartnerWebhookResponse(1L, 9L, "https://partner.example", "BOOKING_CANCELLED", WebhookStatus.DISABLED, Instant.now()));

        PartnerWebhookResponse result = webhookService.updateStatus("admin1", 1L, WebhookStatus.DISABLED);

        assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.DISABLED);
        assertThat(result.status()).isEqualTo(WebhookStatus.DISABLED);
        verify(auditService).record("admin1", "PARTNER_WEBHOOK_STATUS_CHANGED", "PARTNER_WEBHOOK", 1L, "ACTIVE -> DISABLED");
    }

    @Test
    void updateStatus_whenMissing_throwsPartnerWebhookNotFoundException() {
        given(webhookRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> webhookService.updateStatus("admin1", 99L, WebhookStatus.DISABLED))
                .isInstanceOf(PartnerWebhookNotFoundException.class);
    }
}
