package com.ticketwave.catalog.service;

import com.ticketwave.catalog.dto.DriverRequest;
import com.ticketwave.catalog.dto.DriverResponse;

import java.util.List;

public interface DriverService {

    /**
     * Operator-only. Creates a driver owned by the authenticated operator.
     */
    DriverResponse createDriver(String operatorUsername, DriverRequest request);

    /**
     * Operator-only. Lists every driver owned by the authenticated operator.
     */
    List<DriverResponse> listMyDrivers(String operatorUsername);
}
