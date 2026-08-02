package com.ticketwave.pricing.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.pricing.dto.PromoCodeRequest;
import com.ticketwave.pricing.dto.PromoCodeResponse;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.pricing.exception.DuplicatePromoCodeException;
import com.ticketwave.pricing.exception.PromoCodeNotFoundException;
import com.ticketwave.pricing.mapper.PromoCodeMapper;
import com.ticketwave.pricing.repository.PromoCodeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PromoCodeServiceImpl implements PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeMapper promoCodeMapper;
    private final AuditService auditService;

    public PromoCodeServiceImpl(PromoCodeRepository promoCodeRepository, PromoCodeMapper promoCodeMapper, AuditService auditService) {
        this.promoCodeRepository = promoCodeRepository;
        this.promoCodeMapper = promoCodeMapper;
        this.auditService = auditService;
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PromoCodeResponse createPromoCode(String actorUsername, PromoCodeRequest request) {
        if (promoCodeRepository.existsByCode(request.code())) {
            throw new DuplicatePromoCodeException(request.code());
        }

        PromoCode saved = promoCodeRepository.save(promoCodeMapper.toEntity(request));

        auditService.record(actorUsername, "PROMO_CODE_CREATED", "PROMO_CODE", saved.getId(), "code=" + saved.getCode());
        return promoCodeMapper.toResponse(saved);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<PromoCodeResponse> listPromoCodes() {
        return promoCodeRepository.findAll().stream()
                .map(promoCodeMapper::toResponse)
                .toList();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PromoCodeResponse updateStatus(String actorUsername, Long promoCodeId, boolean active) {
        PromoCode promoCode = promoCodeRepository.findById(promoCodeId)
                .orElseThrow(() -> new PromoCodeNotFoundException(promoCodeId));

        promoCode.setActive(active);

        auditService.record(actorUsername, "PROMO_CODE_STATUS_CHANGED", "PROMO_CODE", promoCodeId, "active=" + active);
        return promoCodeMapper.toResponse(promoCode);
    }
}
