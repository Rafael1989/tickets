package com.ticketwave.partner.service;

import com.ticketwave.catalog.dto.RouteResponse;
import com.ticketwave.catalog.mapper.RouteMapper;
import com.ticketwave.catalog.repository.RouteRepository;
import com.ticketwave.partner.entity.PartnerApiCredential;
import com.ticketwave.partner.entity.PartnerCredentialStatus;
import com.ticketwave.partner.entity.PartnerStatus;
import com.ticketwave.partner.exception.InvalidPartnerCredentialsException;
import com.ticketwave.partner.repository.PartnerApiCredentialRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PartnerResourceServiceImpl implements PartnerResourceService {

    private final PartnerApiCredentialRepository credentialRepository;
    private final RouteRepository routeRepository;
    private final RouteMapper routeMapper;

    public PartnerResourceServiceImpl(
            PartnerApiCredentialRepository credentialRepository,
            RouteRepository routeRepository,
            RouteMapper routeMapper
    ) {
        this.credentialRepository = credentialRepository;
        this.routeRepository = routeRepository;
        this.routeMapper = routeMapper;
    }

    @Override
    @PreAuthorize("hasRole('PARTNER_API')")
    @Transactional(readOnly = true)
    public List<RouteResponse> listRoutes(String clientId) {
        PartnerApiCredential credential = credentialRepository.findByClientId(clientId)
                .orElseThrow(InvalidPartnerCredentialsException::new);

        if (credential.getStatus() != PartnerCredentialStatus.ACTIVE
                || credential.getPartner().getStatus() != PartnerStatus.ACTIVE) {
            throw new InvalidPartnerCredentialsException();
        }

        return routeRepository.findByOperatorPartnerId(credential.getPartner().getId()).stream()
                .map(routeMapper::toResponse)
                .toList();
    }
}
