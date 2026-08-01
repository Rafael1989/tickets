package com.ticketwave.pricing.entity;

import com.ticketwave.catalog.entity.Route;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A seasonal fare adjustment for one route/seat class, active over
 * [validFrom, validTo]. Applied as an additional adjustment on top of the
 * existing demand-based multiplier in PricingServiceImpl - see
 * calculateFareRuleAdjustment there. surchargeRate follows the same
 * convention as PricingProperties' own rates: a positive value is a
 * surcharge, negative is a discount.
 */
@Entity
@Table(name = "fare_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FareRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fare_rule_id")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @Column(name = "seat_class", nullable = false, length = 20)
    private String seatClass;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "surcharge_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal surchargeRate;
}
