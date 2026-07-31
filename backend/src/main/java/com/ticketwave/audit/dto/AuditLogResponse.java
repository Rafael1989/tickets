package com.ticketwave.audit.dto;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String actorUsername,
        String action,
        String entityType,
        Long entityId,
        String details,
        Instant createdAt
) {
}
