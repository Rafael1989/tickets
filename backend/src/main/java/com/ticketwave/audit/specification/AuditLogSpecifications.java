package com.ticketwave.audit.specification;

import com.ticketwave.audit.dto.AuditLogSearchCriteria;
import com.ticketwave.audit.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> matching(AuditLogSearchCriteria criteria) {
        return Specification.allOf(
                hasActor(criteria.actor()),
                hasAction(criteria.action()),
                hasEntityType(criteria.entityType()),
                createdFrom(criteria.from()),
                createdTo(criteria.to())
        );
    }

    public static Specification<AuditLog> hasActor(String actor) {
        return (root, query, cb) -> isBlank(actor)
                ? null
                : cb.like(cb.lower(root.get("actorUsername")), likePattern(actor), LIKE_ESCAPE);
    }

    public static Specification<AuditLog> hasAction(String action) {
        return (root, query, cb) -> isBlank(action)
                ? null
                : cb.equal(cb.upper(root.get("action")), action.trim().toUpperCase());
    }

    public static Specification<AuditLog> hasEntityType(String entityType) {
        return (root, query, cb) -> isBlank(entityType)
                ? null
                : cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase());
    }

    public static Specification<AuditLog> createdFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<AuditLog> createdTo(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static final char LIKE_ESCAPE = '\\';

    private static String likePattern(String value) {
        String escaped = value.trim().toLowerCase()
                .replace(String.valueOf(LIKE_ESCAPE), "" + LIKE_ESCAPE + LIKE_ESCAPE)
                .replace("%", LIKE_ESCAPE + "%")
                .replace("_", LIKE_ESCAPE + "_");
        return "%" + escaped + "%";
    }
}
