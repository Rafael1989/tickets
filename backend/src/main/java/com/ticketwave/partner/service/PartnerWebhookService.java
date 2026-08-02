package com.ticketwave.partner.service;

import com.ticketwave.partner.dto.PartnerWebhookIssuedResponse;
import com.ticketwave.partner.dto.PartnerWebhookRequest;
import com.ticketwave.partner.dto.PartnerWebhookResponse;
import com.ticketwave.partner.entity.WebhookStatus;

import java.util.List;

public interface PartnerWebhookService {

    /**
     * Admin-only. Registers a new webhook target and generates its signing
     * secret — only ever present in this call's response, see
     * PartnerWebhookIssuedResponse.
     *
     * @throws com.ticketwave.partner.exception.PartnerNotFoundException if no such partner exists
     */
    PartnerWebhookIssuedResponse registerWebhook(String actorUsername, Long partnerId, PartnerWebhookRequest request);

    /**
     * Admin-only. Lists a partner's webhooks — never includes a secret.
     */
    List<PartnerWebhookResponse> listWebhooks(Long partnerId);

    /**
     * Admin-only. Enables or disables delivery to this webhook without
     * deleting its registration/history.
     *
     * @throws com.ticketwave.partner.exception.PartnerWebhookNotFoundException if no such webhook exists
     */
    PartnerWebhookResponse updateStatus(String actorUsername, Long webhookId, WebhookStatus status);
}
