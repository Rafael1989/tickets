package com.ticketwave.partner.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.partner.dto.PartnerRequest;
import com.ticketwave.partner.dto.PartnerResponse;
import com.ticketwave.partner.entity.Partner;
import com.ticketwave.partner.entity.PartnerStatus;
import com.ticketwave.partner.exception.DuplicatePartnerException;
import com.ticketwave.partner.exception.PartnerNotFoundException;
import com.ticketwave.partner.mapper.PartnerMapper;
import com.ticketwave.partner.repository.PartnerRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PartnerServiceImpl implements PartnerService {

    private final PartnerRepository partnerRepository;
    private final PartnerMapper partnerMapper;
    private final AuditService auditService;

    public PartnerServiceImpl(PartnerRepository partnerRepository, PartnerMapper partnerMapper, AuditService auditService) {
        this.partnerRepository = partnerRepository;
        this.partnerMapper = partnerMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PartnerResponse createPartner(String actorUsername, PartnerRequest request) {
        if (partnerRepository.existsByName(request.name())) {
            throw new DuplicatePartnerException(request.name());
        }

        Partner partner = partnerMapper.toEntity(request);
        partner.setStatus(PartnerStatus.PENDING);
        Partner saved = partnerRepository.save(partner);

        auditService.record(actorUsername, "PARTNER_CREATED", "PARTNER", saved.getId(), "name=" + saved.getName());
        return partnerMapper.toResponse(saved);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PartnerResponse getPartner(Long partnerId) {
        return partnerRepository.findById(partnerId)
                .map(partnerMapper::toResponse)
                .orElseThrow(() -> new PartnerNotFoundException(partnerId));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<PartnerResponse> listPartners() {
        return partnerRepository.findAll().stream()
                .map(partnerMapper::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PartnerResponse updateStatus(String actorUsername, Long partnerId, PartnerStatus status) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new PartnerNotFoundException(partnerId));

        PartnerStatus previousStatus = partner.getStatus();
        partner.setStatus(status);

        auditService.record(actorUsername, "PARTNER_STATUS_CHANGED", "PARTNER", partnerId,
                previousStatus + " -> " + status);
        return partnerMapper.toResponse(partner);
    }
}
