package com.ticketwave.partner.repository;

import com.ticketwave.partner.entity.WebhookDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, Long> {

    List<WebhookDeliveryLog> findByWebhookIdOrderByAttemptedAtDesc(Long webhookId);
}
