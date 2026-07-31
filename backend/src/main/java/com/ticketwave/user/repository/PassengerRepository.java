package com.ticketwave.user.repository;

import com.ticketwave.user.entity.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {

    List<Passenger> findByUserId(Long userId);
}
