package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.VehicleRequest;
import com.ticketwave.catalog.dto.VehicleResponse;

import java.util.List;

public interface VehicleService {

    /**
     * Operator-only. Creates a vehicle owned by the authenticated operator.
     */
    VehicleResponse createVehicle(String operatorUsername, VehicleRequest request);

    /**
     * Operator-only. Lists every vehicle owned by the authenticated operator.
     */
    List<VehicleResponse> listMyVehicles(String operatorUsername);
}
