package com.ticketwave.catalog.repository;

import com.ticketwave.catalog.entity.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    List<Driver> findByOperatorId(Long operatorId);
}
