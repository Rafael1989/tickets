package com.ticketwave.audit.dto;

import java.time.Instant;

/**
 * Every field is optional; an all-null criteria matches every audit entry.
 * actor matches a substring of actorUsername (case-insensitive); action and
 * entityType match exactly (case-insensitive) since both are fixed codes,
 * not free text.
 */
public record AuditLogSearchCriteria(
        String actor,
        String action,
        String entityType,
        Instant from,
        Instant to
) {
}
