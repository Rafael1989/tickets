package com.ticketwave.pricing.repository;

import com.ticketwave.pricing.entity.FareRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FareRuleRepository extends JpaRepository<FareRule, Long> {

    List<FareRule> findByRouteId(Long routeId);

    /** Rules for this route/seat class whose [validFrom, validTo) window covers "at" - a schedule's own departure time. */
    @Query("""
            SELECT f FROM FareRule f
            WHERE f.route.id = :routeId AND f.seatClass = :seatClass
            AND f.validFrom <= :at AND f.validTo > :at
            """)
    List<FareRule> findActive(@Param("routeId") Long routeId, @Param("seatClass") String seatClass, @Param("at") Instant at);
}
