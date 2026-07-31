package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.RouteRequest;
import com.ticketwave.catalog.dto.RouteResponse;

import java.util.List;

public interface RouteService {

    /**
     * Operator-only. Creates a route owned by the authenticated operator.
     */
    RouteResponse createRoute(String operatorUsername, RouteRequest request);

    /**
     * Operator-only. Lists every route owned by the authenticated operator.
     */
    List<RouteResponse> listMyRoutes(String operatorUsername);
}
