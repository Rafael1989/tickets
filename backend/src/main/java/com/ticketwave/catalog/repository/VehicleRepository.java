package com.ticketwave.catalog.repository;

import com.ticketwave.catalog.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByOperatorId(Long operatorId);
}
