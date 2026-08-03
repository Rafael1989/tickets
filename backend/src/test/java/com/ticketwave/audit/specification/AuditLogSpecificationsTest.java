package com.ticketwave.audit.specification;

import com.ticketwave.audit.dto.AuditLogSearchCriteria;
import com.ticketwave.audit.entity.AuditLog;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SuppressWarnings({"unchecked", "rawtypes"})
@ExtendWith(MockitoExtension.class)
class AuditLogSpecificationsTest {

    @Mock
    private Root<AuditLog> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path fieldPath;
    @Mock
    private Predicate predicate;

    @Test
    void hasActor_withNull_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.hasActor(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasActor_withBlank_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.hasActor("   ").toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasActor_withValue_buildsCaseInsensitivePartialMatchPredicate() {
        given(root.get("actorUsername")).willReturn(fieldPath);
        given(cb.lower(fieldPath)).willReturn(fieldPath);
        given(cb.like(fieldPath, "%alice%", '\\')).willReturn(predicate);

        Predicate result = AuditLogSpecifications.hasActor(" Alice ").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void hasActor_withLikeWildcardsInValue_escapesThemInThePattern() {
        given(root.get("actorUsername")).willReturn(fieldPath);
        given(cb.lower(fieldPath)).willReturn(fieldPath);
        given(cb.like(fieldPath, "%a\\\\b\\%c\\_d%", '\\')).willReturn(predicate);

        Predicate result = AuditLogSpecifications.hasActor("a\\b%c_d").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void hasAction_withNull_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.hasAction(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasAction_withBlank_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.hasAction("").toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasAction_withValue_buildsCaseInsensitiveEqualityPredicate() {
        given(root.get("action")).willReturn(fieldPath);
        given(cb.upper(fieldPath)).willReturn(fieldPath);
        given(cb.equal(fieldPath, "REFUND_ISSUED")).willReturn(predicate);

        Predicate result = AuditLogSpecifications.hasAction(" refund_issued ").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void hasEntityType_withNull_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.hasEntityType(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasEntityType_withBlank_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.hasEntityType("  ").toPredicate(root, query, cb)).isNull();
    }

    @Test
    void hasEntityType_withValue_buildsCaseInsensitiveEqualityPredicate() {
        given(root.get("entityType")).willReturn(fieldPath);
        given(cb.upper(fieldPath)).willReturn(fieldPath);
        given(cb.equal(fieldPath, "BOOKING")).willReturn(predicate);

        Predicate result = AuditLogSpecifications.hasEntityType(" booking ").toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void createdFrom_withNull_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.createdFrom(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void createdFrom_withInstant_buildsGreaterThanOrEqualPredicateOnCreatedAt() {
        Instant from = Instant.parse("2026-01-15T00:00:00Z");
        given(root.get("createdAt")).willReturn(fieldPath);
        given(cb.greaterThanOrEqualTo(fieldPath, from)).willReturn(predicate);

        Predicate result = AuditLogSpecifications.createdFrom(from).toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void createdTo_withNull_returnsNoPredicate() {
        assertThat(AuditLogSpecifications.createdTo(null).toPredicate(root, query, cb)).isNull();
    }

    @Test
    void createdTo_withInstant_buildsLessThanOrEqualPredicateOnCreatedAt() {
        Instant to = Instant.parse("2026-01-16T00:00:00Z");
        given(root.get("createdAt")).willReturn(fieldPath);
        given(cb.lessThanOrEqualTo(fieldPath, to)).willReturn(predicate);

        Predicate result = AuditLogSpecifications.createdTo(to).toPredicate(root, query, cb);

        assertThat(result).isSameAs(predicate);
    }

    @Test
    void matching_combinesAllCriteriaIntoASingleSpecification() {
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(
                "alice", "REFUND_ISSUED", "BOOKING",
                Instant.parse("2026-01-15T00:00:00Z"), Instant.parse("2026-01-16T00:00:00Z"));

        Specification<AuditLog> specification = AuditLogSpecifications.matching(criteria);

        assertThat(specification).isNotNull();
    }

    @Test
    void matching_withAllNullCriteria_stillReturnsASpecification() {
        AuditLogSearchCriteria criteria = new AuditLogSearchCriteria(null, null, null, null, null);

        Specification<AuditLog> specification = AuditLogSpecifications.matching(criteria);

        assertThat(specification).isNotNull();
    }
}
