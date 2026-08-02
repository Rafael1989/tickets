package com.ticketwave.pricing.service;

import com.ticketwave.audit.service.AuditService;
import com.ticketwave.pricing.dto.PromoCodeRequest;
import com.ticketwave.pricing.dto.PromoCodeResponse;
import com.ticketwave.pricing.entity.DiscountType;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.pricing.exception.DuplicatePromoCodeException;
import com.ticketwave.pricing.exception.PromoCodeNotFoundException;
import com.ticketwave.pricing.mapper.PromoCodeMapper;
import com.ticketwave.pricing.repository.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceImplTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PromoCodeMapper promoCodeMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PromoCodeServiceImpl promoCodeService;

    private static PromoCodeRequest request() {
        return new PromoCodeRequest("SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2030-12-31T00:00:00Z"), null);
    }

    @Test
    void createPromoCode_whenCodeAvailable_savesAndAudits() {
        PromoCode mapped = PromoCode.builder().code("SAVE20").discountType(DiscountType.PERCENTAGE).build();
        PromoCode saved = PromoCode.builder().id(1L).code("SAVE20").build();
        given(promoCodeRepository.existsByCode("SAVE20")).willReturn(false);
        given(promoCodeMapper.toEntity(request())).willReturn(mapped);
        given(promoCodeRepository.save(mapped)).willReturn(saved);
        given(promoCodeMapper.toResponse(saved)).willReturn(
                new PromoCodeResponse(1L, "SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                        Instant.now(), Instant.now(), null, 0, true, Instant.now()));

        PromoCodeResponse result = promoCodeService.createPromoCode("admin1", request());

        assertThat(result.code()).isEqualTo("SAVE20");
        verify(auditService).record("admin1", "PROMO_CODE_CREATED", "PROMO_CODE", 1L, "code=SAVE20");
    }

    @Test
    void createPromoCode_whenCodeTaken_throwsDuplicatePromoCodeExceptionAndNeverSaves() {
        given(promoCodeRepository.existsByCode("SAVE20")).willReturn(true);

        assertThatThrownBy(() -> promoCodeService.createPromoCode("admin1", request()))
                .isInstanceOf(DuplicatePromoCodeException.class);

        verify(promoCodeRepository, never()).save(any());
    }

    @Test
    void listPromoCodes_returnsEveryPromoCodeMappedToResponse() {
        PromoCode promoCode = PromoCode.builder().id(1L).code("SAVE20").build();
        given(promoCodeRepository.findAll()).willReturn(List.of(promoCode));
        PromoCodeResponse response = new PromoCodeResponse(1L, "SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                Instant.now(), Instant.now(), null, 0, true, Instant.now());
        given(promoCodeMapper.toResponse(promoCode)).willReturn(response);

        assertThat(promoCodeService.listPromoCodes()).containsExactly(response);
    }

    @Test
    void updateStatus_whenFound_changesActiveFlagAndAudits() {
        PromoCode promoCode = PromoCode.builder().id(1L).code("SAVE20").active(true).build();
        given(promoCodeRepository.findById(1L)).willReturn(Optional.of(promoCode));
        given(promoCodeMapper.toResponse(promoCode)).willReturn(
                new PromoCodeResponse(1L, "SAVE20", DiscountType.PERCENTAGE, new BigDecimal("20.00"),
                        Instant.now(), Instant.now(), null, 0, false, Instant.now()));

        PromoCodeResponse result = promoCodeService.updateStatus("admin1", 1L, false);

        assertThat(promoCode.getActive()).isFalse();
        assertThat(result.active()).isFalse();
        verify(auditService).record("admin1", "PROMO_CODE_STATUS_CHANGED", "PROMO_CODE", 1L, "active=false");
    }

    @Test
    void updateStatus_whenMissing_throwsPromoCodeNotFoundException() {
        given(promoCodeRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> promoCodeService.updateStatus("admin1", 99L, false))
                .isInstanceOf(PromoCodeNotFoundException.class);
    }
}
