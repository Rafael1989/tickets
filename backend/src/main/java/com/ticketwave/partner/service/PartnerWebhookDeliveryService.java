package com.ticketwave.partner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketwave.partner.entity.PartnerWebhook;
import com.ticketwave.partner.entity.WebhookDeliveryLog;
import com.ticketwave.partner.entity.WebhookStatus;
import com.ticketwave.partner.repository.PartnerWebhookRepository;
import com.ticketwave.partner.repository.WebhookDeliveryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;

/**
 * Fires an event to every ACTIVE webhook a partner has registered for it,
 * off the calling request's thread (see @EnableAsync on
 * TicketwaveApplication) so a slow/unreachable partner endpoint never delays
 * the customer-facing action that triggered the event. One retry after a
 * short fixed delay, then the outcome (success or failure) is always logged
 * to WebhookDeliveryLog — this is best-effort, at-least-once-attempted
 * delivery, not a durable queue with unlimited retries; a partner endpoint
 * down longer than that needs to notice its own gap and ask support to
 * replay from the delivery log, since there's no persistent retry queue here.
 */
@Service
public class PartnerWebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(PartnerWebhookDeliveryService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long RETRY_DELAY_MILLIS = 500;

    private final PartnerWebhookRepository webhookRepository;
    private final WebhookDeliveryLogRepository deliveryLogRepository;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    public PartnerWebhookDeliveryService(
            PartnerWebhookRepository webhookRepository,
            WebhookDeliveryLogRepository deliveryLogRepository,
            ObjectMapper objectMapper
    ) {
        this.webhookRepository = webhookRepository;
        this.deliveryLogRepository = deliveryLogRepository;
        this.objectMapper = objectMapper;
    }

    @Async
    public void deliver(Long partnerId, String eventType, Object payload) {
        List<PartnerWebhook> webhooks = webhookRepository.findByPartnerIdAndEventTypeAndStatus(
                partnerId, eventType, WebhookStatus.ACTIVE);
        if (webhooks.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize webhook payload for partner {} event {}", partnerId, eventType, ex);
            return;
        }

        for (PartnerWebhook webhook : webhooks) {
            attemptDelivery(webhook, eventType, json);
        }
    }

    @Transactional
    void attemptDelivery(PartnerWebhook webhook, String eventType, String json) {
        String signature = sign(webhook.getSecret(), json);

        DeliveryOutcome outcome = attempt(webhook.getUrl(), json, signature);
        if (!outcome.success()) {
            sleepBeforeRetry();
            outcome = attempt(webhook.getUrl(), json, signature);
        }

        deliveryLogRepository.save(WebhookDeliveryLog.builder()
                .webhook(webhook)
                .eventType(eventType)
                .payload(json)
                .responseStatus(outcome.responseStatus())
                .success(outcome.success())
                .errorMessage(outcome.errorMessage())
                .build());
    }

    private DeliveryOutcome attempt(String url, String json, String signature) {
        try {
            var response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .header("X-TicketWave-Signature", "sha256=" + signature)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
            int status = response.getStatusCode().value();
            return new DeliveryOutcome(status < 300, status, null);
        } catch (RestClientResponseException ex) {
            return new DeliveryOutcome(false, ex.getStatusCode().value(), ex.getMessage());
        } catch (RestClientException ex) {
            return new DeliveryOutcome(false, null, ex.getMessage());
        }
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC signing failed", ex);
        }
    }

    private record DeliveryOutcome(boolean success, Integer responseStatus, String errorMessage) {
    }
}
