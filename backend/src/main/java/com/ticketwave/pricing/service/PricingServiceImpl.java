package com.ticketwave.pricing.service;

import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.PricingProperties;
import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.pricing.exception.PromoCodeNotApplicableException;
import com.ticketwave.pricing.exception.PromoCodeNotFoundException;
import com.ticketwave.pricing.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class PricingServiceImpl implements PricingService {

    /**
     * Defensive floor: no combination of demand adjustments should ever be
     * able to drive a fare to zero or negative, even under a misconfigured
     * PricingProperties.
     */
    private static final BigDecimal MIN_DEMAND_MULTIPLIER = new BigDecimal("0.10");

    private final SeatRepository seatRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PricingProperties pricingProperties;

    public PricingServiceImpl(
            SeatRepository seatRepository,
            PromoCodeRepository promoCodeRepository,
            PricingProperties pricingProperties
    ) {
        this.seatRepository = seatRepository;
        this.promoCodeRepository = promoCodeRepository;
        this.pricingProperties = pricingProperties;
    }

    @Override
    public BigDecimal calculateSeatFare(Schedule schedule, Seat seat) {
        BigDecimal demandMultiplier = calculateDemandMultiplier(schedule);
        return schedule.getBaseFare()
                .multiply(seat.getPriceModifier())
                .multiply(demandMultiplier)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public PromoCodeApplication applyPromoCode(String code, BigDecimal subtotal) {
        PromoCode promoCode = promoCodeRepository.findByCodeForUpdate(code)
                .orElseThrow(() -> new PromoCodeNotFoundException(code));

        validateUsable(promoCode);

        BigDecimal discount = calculateDiscount(promoCode, subtotal);
        promoCode.setRedemptionCount(promoCode.getRedemptionCount() + 1);

        return new PromoCodeApplication(promoCode, discount);
    }

    private void validateUsable(PromoCode promoCode) {
        if (!Boolean.TRUE.equals(promoCode.getActive())) {
            throw new PromoCodeNotApplicableException(promoCode.getCode(), "inactive");
        }

        Instant now = Instant.now();
        if (now.isBefore(promoCode.getValidFrom()) || now.isAfter(promoCode.getValidTo())) {
            throw new PromoCodeNotApplicableException(promoCode.getCode(), "outside its validity window");
        }

        Integer maxRedemptions = promoCode.getMaxRedemptions();
        if (maxRedemptions != null && promoCode.getRedemptionCount() >= maxRedemptions) {
            throw new PromoCodeNotApplicableException(promoCode.getCode(), "fully redeemed");
        }
    }

    private BigDecimal calculateDiscount(PromoCode promoCode, BigDecimal subtotal) {
        BigDecimal discount = switch (promoCode.getDiscountType()) {
            case PERCENTAGE -> subtotal
                    .multiply(promoCode.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> promoCode.getDiscountValue();
        };
        // Never discount more than the subtotal itself.
        return discount.min(subtotal);
    }

    private BigDecimal calculateDemandMultiplier(Schedule schedule) {
        BigDecimal adjustment = BigDecimal.ZERO;

        Duration untilDeparture = Duration.between(Instant.now(), schedule.getDepartureTime());
        if (!untilDeparture.isNegative() && untilDeparture.toHours() <= pricingProperties.lastMinuteThresholdHours()) {
            adjustment = adjustment.add(pricingProperties.lastMinuteSurchargeRate());
        } else if (untilDeparture.toDays() >= pricingProperties.earlyBirdThresholdDays()) {
            adjustment = adjustment.subtract(pricingProperties.earlyBirdDiscountRate());
        }

        BigDecimal occupancy = calculateOccupancy(schedule);
        if (occupancy.compareTo(pricingProperties.highOccupancyThreshold()) >= 0) {
            adjustment = adjustment.add(pricingProperties.highOccupancySurchargeRate());
        } else if (occupancy.compareTo(pricingProperties.lowOccupancyThreshold()) <= 0) {
            adjustment = adjustment.subtract(pricingProperties.lowOccupancyDiscountRate());
        }

        BigDecimal multiplier = BigDecimal.ONE.add(adjustment);
        return multiplier.max(MIN_DEMAND_MULTIPLIER);
    }

    private BigDecimal calculateOccupancy(Schedule schedule) {
        long total = seatRepository.countByScheduleId(schedule.getId());
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        long available = seatRepository.countByScheduleIdAndStatus(schedule.getId(), SeatStatus.AVAILABLE);
        long taken = total - available;
        return BigDecimal.valueOf(taken).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }
}
