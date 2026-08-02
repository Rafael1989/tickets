package com.ticketwave.partner.repository;

import com.ticketwave.partner.entity.PartnerWebhook;
import com.ticketwave.partner.entity.WebhookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PartnerWebhookRepository extends JpaRepository<PartnerWebhook, Long> {

    List<PartnerWebhook> findByPartnerId(Long partnerId);

    List<PartnerWebhook> findByPartnerIdAndEventTypeAndStatus(Long partnerId, String eventType, WebhookStatus status);
}
