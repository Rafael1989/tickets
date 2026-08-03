package com.ticketwave.partner.service;

import com.ticketwave.AbstractIntegrationTest;
import com.ticketwave.partner.dto.PartnerCredentialIssuedResponse;
import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.dto.PartnerTokenResponse;
import com.ticketwave.partner.dto.PartnerWebhookIssuedResponse;
import com.ticketwave.partner.dto.PartnerWebhookRequest;
import com.ticketwave.partner.entity.PartnerCredentialStatus;
import com.ticketwave.partner.entity.PartnerStatus;
import com.ticketwave.partner.entity.PartnerWebhook;
import com.ticketwave.partner.entity.WebhookDeliveryLog;
import com.ticketwave.partner.exception.InvalidPartnerCredentialsException;
import com.ticketwave.partner.repository.PartnerApiCredentialRepository;
import com.ticketwave.partner.repository.PartnerWebhookRepository;
import com.ticketwave.partner.repository.WebhookDeliveryLogRepository;
import com.ticketwave.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The partner lifecycle against a real database: onboard -> activate -> issue
 * credentials -> exchange for a token -> register a webhook -> record a
 * delivery attempt.
 *
 * Covers ground the unit tests structurally cannot. Every entity here defaults
 * its createdAt in a @PrePersist hook, which only runs when JPA actually
 * flushes - a Mockito-mocked repository never triggers one. It also proves the
 * credential's BCrypt hash round-trips through the token exchange, and that
 * PartnerStatus gates it, both of which depend on real persistence.
 */
class PartnerOnboardingIT extends AbstractIntegrationTest {

    @Autowired
    private PartnerService partnerService;
    @Autowired
    private PartnerApiCredentialService credentialService;
    @Autowired
    private PartnerWebhookService webhookService;
    @Autowired
    private PartnerWebhookDeliveryService deliveryService;
    @Autowired
    private PartnerApiCredentialRepository credentialRepository;
    @Autowired
    private PartnerWebhookRepository webhookRepository;
    @Autowired
    private WebhookDeliveryLogRepository deliveryLogRepository;

    private static final String ADMIN = "admin-partner-it";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ADMIN, null, List.of(new SimpleGrantedAuthority("ROLE_" + UserRole.ADMIN.name()))));
    }

    private PartnerResponse newPartner() {
        authenticateAsAdmin();
        return partnerService.createPartner(ADMIN, new PartnerRequest(
                "Partner " + uniqueSuffix(),
                "partner-" + uniqueSuffix() + "@example.com",
                new BigDecimal("0.1000")));
    }

    @Test
    void createPartner_persistsAsPendingWithACreatedAtDefaultedByThePrePersistHook() {
        PartnerResponse created = newPartner();

        assertThat(created.status()).isEqualTo(PartnerStatus.PENDING);
        assertThat(created.createdAt()).isNotNull();
    }

    @Test
    void issueTokenForAnActivePartner_acceptsTheOneTimeSecretAndStampsLastUsedAt() {
        PartnerResponse partner = newPartner();
        partnerService.updateStatus(ADMIN, partner.id(), PartnerStatus.ACTIVE);

        PartnerCredentialIssuedResponse issued = credentialService.issueCredential(ADMIN, partner.id());
        assertThat(issued.clientId()).startsWith("pk_");
        assertThat(issued.createdAt()).isNotNull();

        // The raw secret is never stored - only its BCrypt hash - so this also
        // proves the hash round-trips through a real insert and re-read.
        assertThat(credentialRepository.findByClientId(issued.clientId()).orElseThrow().getClientSecretHash())
                .isNotEqualTo(issued.clientSecret());

        PartnerTokenResponse token = credentialService.issueToken(issued.clientId(), issued.clientSecret());

        assertThat(token.accessToken()).isNotBlank();
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(credentialRepository.findByClientId(issued.clientId()).orElseThrow().getLastUsedAt()).isNotNull();
    }

    @Test
    void issueToken_whenPartnerIsStillPending_isRejectedEvenWithTheCorrectSecret() {
        PartnerResponse partner = newPartner();
        PartnerCredentialIssuedResponse issued = credentialService.issueCredential(ADMIN, partner.id());

        assertThatThrownBy(() -> credentialService.issueToken(issued.clientId(), issued.clientSecret()))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }

    @Test
    void issueToken_afterTheCredentialIsRevoked_isRejected() {
        PartnerResponse partner = newPartner();
        partnerService.updateStatus(ADMIN, partner.id(), PartnerStatus.ACTIVE);
        PartnerCredentialIssuedResponse issued = credentialService.issueCredential(ADMIN, partner.id());

        credentialService.revokeCredential(ADMIN, issued.id());

        assertThat(credentialRepository.findById(issued.id()).orElseThrow().getStatus())
                .isEqualTo(PartnerCredentialStatus.REVOKED);
        assertThatThrownBy(() -> credentialService.issueToken(issued.clientId(), issued.clientSecret()))
                .isInstanceOf(InvalidPartnerCredentialsException.class);
    }

    @Test
    void registerWebhook_persistsTheSigningSecretAndDefaultsCreatedAt() {
        PartnerResponse partner = newPartner();

        PartnerWebhookIssuedResponse issued = webhookService.registerWebhook(ADMIN, partner.id(),
                new PartnerWebhookRequest("https://example.invalid/hooks", "BOOKING_CANCELLED"));

        assertThat(issued.secret()).isNotBlank();
        assertThat(issued.createdAt()).isNotNull();
        // Unlike the credential secret, the signing secret is stored as-is: it
        // is the HMAC key, so a one-way hash would make signing impossible.
        assertThat(webhookRepository.findById(issued.id()).orElseThrow().getSecret()).isEqualTo(issued.secret());
    }

    /**
     * Points at an unroutable address on purpose: a delivery attempt must be
     * logged whether or not the endpoint answers, since an unlogged failure is
     * indistinguishable from a delivery that never happened.
     */
    @Test
    void attemptDelivery_whenTheTargetIsUnreachable_stillPersistsAFailedDeliveryLog() {
        PartnerResponse partner = newPartner();
        PartnerWebhookIssuedResponse issued = webhookService.registerWebhook(ADMIN, partner.id(),
                new PartnerWebhookRequest("http://127.0.0.1:1/hooks", "BOOKING_CANCELLED"));
        PartnerWebhook webhook = webhookRepository.findById(issued.id()).orElseThrow();

        deliveryService.attemptDelivery(webhook, "BOOKING_CANCELLED", "{\"bookingId\":1}");

        List<WebhookDeliveryLog> logs = deliveryLogRepository.findAll().stream()
                .filter(entry -> entry.getWebhook().getId().equals(webhook.getId()))
                .toList();

        assertThat(logs).hasSize(1);
        WebhookDeliveryLog entry = logs.getFirst();
        assertThat(entry.isSuccess()).isFalse();
        assertThat(entry.getEventType()).isEqualTo("BOOKING_CANCELLED");
        assertThat(entry.getErrorMessage()).isNotBlank();
        assertThat(entry.getAttemptedAt()).isNotNull();
    }
}
