package com.ticketwave.partner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A partner-registered outbound notification target: TicketWave POSTs an
 * eventType payload to url whenever that event fires for one of the
 * partner's bookings, signed with secret (HMAC-SHA256, see
 * PartnerWebhookDeliveryService). One row per event type a partner cares
 * about, rather than one row fanning out to many event types, so a partner
 * can point different events at different URLs (or disable just one).
 */
@Entity
@Table(name = "partner_webhooks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PartnerWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "webhook_id")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** Plaintext, unlike a password hash — it must be usable to (re-)compute an HMAC signature on every delivery. */
    @Column(name = "secret", nullable = false, length = 100)
    private String secret;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "status", nullable = false, length = 20)
    private WebhookStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
