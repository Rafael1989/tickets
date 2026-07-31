package com.ticketwave.catalog.repository;

import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.RouteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByType(RouteType type);

    List<Route> findByOperatorId(Long operatorId);

    List<Route> findByOriginIgnoreCaseAndDestinationIgnoreCase(String origin, String destination);
}
