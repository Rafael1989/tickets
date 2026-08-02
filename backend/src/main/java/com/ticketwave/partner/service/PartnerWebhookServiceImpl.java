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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class PartnerWebhookServiceImpl implements PartnerWebhookService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PartnerWebhookRepository webhookRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerWebhookMapper webhookMapper;
    private final AuditService auditService;

    public PartnerWebhookServiceImpl(
            PartnerWebhookRepository webhookRepository,
            PartnerRepository partnerRepository,
            PartnerWebhookMapper webhookMapper,
            AuditService auditService
    ) {
        this.webhookRepository = webhookRepository;
        this.partnerRepository = partnerRepository;
        this.webhookMapper = webhookMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PartnerWebhookIssuedResponse registerWebhook(String actorUsername, Long partnerId, PartnerWebhookRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new PartnerNotFoundException(partnerId));

        String secret = randomToken();
        PartnerWebhook webhook = webhookRepository.save(PartnerWebhook.builder()
                .partner(partner)
                .url(request.url())
                .eventType(request.eventType())
                .secret(secret)
                .status(WebhookStatus.ACTIVE)
                .build());

        auditService.record(actorUsername, "PARTNER_WEBHOOK_REGISTERED", "PARTNER_WEBHOOK", webhook.getId(),
                "partnerId=" + partnerId + " eventType=" + request.eventType());
        return new PartnerWebhookIssuedResponse(webhook.getId(), partnerId, webhook.getUrl(), secret,
                webhook.getEventType(), webhook.getCreatedAt());
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<PartnerWebhookResponse> listWebhooks(Long partnerId) {
        return webhookRepository.findByPartnerId(partnerId).stream()
                .map(webhookMapper::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PartnerWebhookResponse updateStatus(String actorUsername, Long webhookId, WebhookStatus status) {
        PartnerWebhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new PartnerWebhookNotFoundException(webhookId));

        WebhookStatus previousStatus = webhook.getStatus();
        webhook.setStatus(status);

        auditService.record(actorUsername, "PARTNER_WEBHOOK_STATUS_CHANGED", "PARTNER_WEBHOOK", webhookId,
                previousStatus + " -> " + status);
        return webhookMapper.toResponse(webhook);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
