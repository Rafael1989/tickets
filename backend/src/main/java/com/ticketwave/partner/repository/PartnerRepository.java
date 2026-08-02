package com.ticketwave.partner.repository;

import com.ticketwave.partner.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, Long> {

    boolean existsByName(String name);
}
