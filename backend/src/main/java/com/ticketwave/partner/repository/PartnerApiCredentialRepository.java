package com.ticketwave.partner.repository;

import com.ticketwave.partner.entity.PartnerApiCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PartnerApiCredentialRepository extends JpaRepository<PartnerApiCredential, Long> {

    Optional<PartnerApiCredential> findByClientId(String clientId);

    List<PartnerApiCredential> findByPartnerId(Long partnerId);
}
