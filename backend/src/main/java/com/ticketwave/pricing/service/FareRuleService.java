package com.ticketwave.pricing.service;

import com.ticketwave.pricing.dto.FareRuleRequest;
import com.ticketwave.pricing.dto.FareRuleResponse;

import java.util.List;

public interface FareRuleService {

    /**
     * Operator-only. Creates a single fare rule under a route owned by the
     * authenticated operator.
     *
     * @throws com.ticketwave.catalog.exception.RouteNotFoundException if the route doesn't exist or isn't owned by this operator
     */
    FareRuleResponse createFareRule(String operatorUsername, FareRuleRequest request);

    /**
     * Operator-only. Bulk-loads fare rules (e.g. from a parsed CSV upload).
     * Every row's route ownership is validated before any row is persisted,
     * so a single bad row can't leave a partial import.
     *
     * @throws com.ticketwave.catalog.exception.RouteNotFoundException if any row's route doesn't exist or isn't owned by this operator
     */
    List<FareRuleResponse> bulkCreateFareRules(String operatorUsername, List<FareRuleRequest> requests);

    /**
     * Operator-only. Lists every fare rule under a route owned by the
     * authenticated operator.
     *
     * @throws com.ticketwave.catalog.exception.RouteNotFoundException if the route doesn't exist or isn't owned by this operator
     */
    List<FareRuleResponse> listFareRulesForRoute(String operatorUsername, Long routeId);
}
