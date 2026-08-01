package com.ticketwave.pricing.service;

import com.ticketwave.catalog.entity.Route;
import com.ticketwave.catalog.entity.Schedule;
import com.ticketwave.catalog.entity.Seat;
import com.ticketwave.catalog.entity.SeatStatus;
import com.ticketwave.catalog.repository.SeatRepository;
import com.ticketwave.config.PricingProperties;
import com.ticketwave.pricing.dto.PromoCodeApplication;
import com.ticketwave.pricing.entity.DiscountType;
import com.ticketwave.pricing.entity.FareRule;
import com.ticketwave.pricing.entity.PromoCode;
import com.ticketwave.pricing.exception.PromoCodeNotApplicableException;
import com.ticketwave.pricing.exception.PromoCodeNotFoundException;
import com.ticketwave.pricing.repository.FareRuleRepository;
import com.ticketwave.pricing.repository.PromoCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class PricingServiceImplTest {

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private FareRuleRepository fareRuleRepository;

    private static final PricingProperties PROPERTIES = new PricingProperties(
            24, new BigDecimal("0.25"),
            30, new BigDecimal("0.10"),
            new BigDecimal("0.80"), new BigDecimal("0.15"),
            new BigDecimal("0.20"), new BigDecimal("0.05"));

    private PricingServiceImpl pricingService;

    private PricingServiceImpl service(PricingProperties properties) {
        return new PricingServiceImpl(seatRepository, promoCodeRepository, fareRuleRepository, properties);
    }

    private static Schedule scheduleDepartingIn(java.time.Duration untilDeparture) {
        Route route = Route.builder().id(1L).build();
        return Schedule.builder()
                .id(1L)
                .route(route)
                .baseFare(new BigDecimal("100.00"))
                .departureTime(Instant.now().plus(untilDeparture))
                .build();
    }

    private static Seat seatWithModifier(BigDecimal modifier) {
        return Seat.builder().id(1L).seatClass("economy").priceModifier(modifier).build();
    }

    private void givenOccupancy(long total, long available) {
        given(seatRepository.countByScheduleId(1L)).willReturn(total);
        given(seatRepository.countByScheduleIdAndStatus(1L, SeatStatus.AVAILABLE)).willReturn(available);
    }

    @Test
    void calculateSeatFare_withNeutralTimingAndOccupancy_appliesNoAdjustment() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(10));
        givenOccupancy(10, 5); // 50% occupancy: between the 20%/80% thresholds

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("100.00");
    }

    @Test
    void calculateSeatFare_lastMinute_appliesSurcharge() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofHours(1));
        givenOccupancy(10, 5);

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("125.00");
    }

    @Test
    void calculateSeatFare_earlyBird_appliesDiscount() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(40));
        givenOccupancy(10, 5);

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("90.00");
    }

    @Test
    void calculateSeatFare_highOccupancy_appliesSurcharge() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(10));
        givenOccupancy(10, 1); // 90% taken

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("115.00");
    }

    @Test
    void calculateSeatFare_lowOccupancy_appliesDiscount() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(10));
        givenOccupancy(10, 9); // 10% taken

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("95.00");
    }

    @Test
    void calculateSeatFare_whenScheduleAlreadyDeparted_skipsLastMinuteSurcharge() {
        pricingService = service(PROPERTIES);
        // A negative untilDeparture short-circuits the last-minute check
        // (!isNegative() is false), falling through to the early-bird branch.
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofHours(-2));
        givenOccupancy(10, 5);

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("100.00");
    }

    @Test
    void calculateSeatFare_withNoSeatsYetProvisioned_treatsOccupancyAsZero() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(10));
        given(seatRepository.countByScheduleId(1L)).willReturn(0L); // total == 0: short-circuits before the availability lookup

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        // zero occupancy is <= the low-occupancy threshold, so the discount applies.
        assertThat(fare).isEqualByComparingTo("95.00");
    }

    @Test
    void calculateSeatFare_underExtremeStackedDiscounts_neverGoesBelowTheMultiplierFloor() {
        PricingProperties extreme = new PricingProperties(
                24, new BigDecimal("0.25"),
                30, new BigDecimal("0.95"),
                new BigDecimal("0.80"), new BigDecimal("0.15"),
                new BigDecimal("0.20"), new BigDecimal("0.90"));
        pricingService = service(extreme);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(40));
        givenOccupancy(10, 9); // low occupancy discount also applies

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        // multiplier would be 1 - 0.95 - 0.90 = -0.85 without the floor; clamped to 0.10.
        assertThat(fare).isEqualByComparingTo("10.00");
    }

    @Test
    void calculateSeatFare_withActiveFareRule_stacksSurchargeOnTopOfDemandAdjustment() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(10));
        givenOccupancy(10, 5); // neutral occupancy, neutral timing
        FareRule rule = FareRule.builder().id(1L).seatClass("economy").surchargeRate(new BigDecimal("0.30")).build();
        given(fareRuleRepository.findActive(1L, "economy", schedule.getDepartureTime())).willReturn(List.of(rule));

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("130.00");
    }

    @Test
    void calculateSeatFare_withMultipleActiveFareRules_sumsTheirRates() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(10));
        givenOccupancy(10, 5);
        FareRule holidayRule = FareRule.builder().id(1L).seatClass("economy").surchargeRate(new BigDecimal("0.10")).build();
        FareRule clearanceRule = FareRule.builder().id(2L).seatClass("economy").surchargeRate(new BigDecimal("-0.05")).build();
        given(fareRuleRepository.findActive(1L, "economy", schedule.getDepartureTime()))
                .willReturn(List.of(holidayRule, clearanceRule));

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("105.00");
    }

    @Test
    void calculateSeatFare_withNoActiveFareRules_appliesNoFareRuleAdjustment() {
        pricingService = service(PROPERTIES);
        Schedule schedule = scheduleDepartingIn(java.time.Duration.ofDays(10));
        givenOccupancy(10, 5);
        given(fareRuleRepository.findActive(1L, "economy", schedule.getDepartureTime())).willReturn(List.of());

        BigDecimal fare = pricingService.calculateSeatFare(schedule, seatWithModifier(BigDecimal.ONE));

        assertThat(fare).isEqualByComparingTo("100.00");
    }

    @Test
    void applyPromoCode_percentageDiscount_computesAmountAndIncrementsRedemption() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("SAVE20").discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600))
                .maxRedemptions(null).redemptionCount(0).active(true)
                .build();
        given(promoCodeRepository.findByCodeForUpdate("SAVE20")).willReturn(Optional.of(promo));

        PromoCodeApplication application = pricingService.applyPromoCode("SAVE20", new BigDecimal("100.00"));

        assertThat(application.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(promo.getRedemptionCount()).isEqualTo(1);
    }

    @Test
    void applyPromoCode_fixedAmountLargerThanSubtotal_isCappedAtSubtotal() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("BIG999").discountType(DiscountType.FIXED_AMOUNT)
                .discountValue(new BigDecimal("999.00"))
                .validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600))
                .maxRedemptions(null).redemptionCount(0).active(true)
                .build();
        given(promoCodeRepository.findByCodeForUpdate("BIG999")).willReturn(Optional.of(promo));

        PromoCodeApplication application = pricingService.applyPromoCode("BIG999", new BigDecimal("50.00"));

        assertThat(application.discountAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void applyPromoCode_whenCodeUnknown_throwsPromoCodeNotFoundException() {
        pricingService = service(PROPERTIES);
        given(promoCodeRepository.findByCodeForUpdate("NOPE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.applyPromoCode("NOPE", BigDecimal.TEN))
                .isInstanceOf(PromoCodeNotFoundException.class);
    }

    @Test
    void applyPromoCode_whenInactive_throwsPromoCodeNotApplicableException() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("OFF").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600))
                .maxRedemptions(null).redemptionCount(0).active(false)
                .build();
        given(promoCodeRepository.findByCodeForUpdate("OFF")).willReturn(Optional.of(promo));

        assertThatThrownBy(() -> pricingService.applyPromoCode("OFF", BigDecimal.TEN))
                .isInstanceOf(PromoCodeNotApplicableException.class);
    }

    @Test
    void applyPromoCode_whenOutsideValidityWindow_throwsPromoCodeNotApplicableException() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("FUTURE").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .validFrom(Instant.now().plusSeconds(3600))
                .validTo(Instant.now().plusSeconds(7200))
                .maxRedemptions(null).redemptionCount(0).active(true)
                .build();
        given(promoCodeRepository.findByCodeForUpdate("FUTURE")).willReturn(Optional.of(promo));

        assertThatThrownBy(() -> pricingService.applyPromoCode("FUTURE", BigDecimal.TEN))
                .isInstanceOf(PromoCodeNotApplicableException.class);
    }

    @Test
    void applyPromoCode_whenValidityWindowAlreadyElapsed_throwsPromoCodeNotApplicableException() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("EXPIRED").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .validFrom(Instant.now().minusSeconds(7200))
                .validTo(Instant.now().minusSeconds(3600))
                .maxRedemptions(null).redemptionCount(0).active(true)
                .build();
        given(promoCodeRepository.findByCodeForUpdate("EXPIRED")).willReturn(Optional.of(promo));

        assertThatThrownBy(() -> pricingService.applyPromoCode("EXPIRED", BigDecimal.TEN))
                .isInstanceOf(PromoCodeNotApplicableException.class);
    }

    @Test
    void applyPromoCode_whenMaxRedemptionsSetButNotYetReached_appliesNormally() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("CAPPED").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600))
                .maxRedemptions(10).redemptionCount(3).active(true)
                .build();
        given(promoCodeRepository.findByCodeForUpdate("CAPPED")).willReturn(Optional.of(promo));

        PromoCodeApplication application = pricingService.applyPromoCode("CAPPED", new BigDecimal("100.00"));

        assertThat(application.discountAmount()).isEqualByComparingTo("10.00");
        assertThat(promo.getRedemptionCount()).isEqualTo(4);
    }

    @Test
    void applyPromoCode_whenFullyRedeemed_throwsPromoCodeNotApplicableException() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("MAXED").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600))
                .maxRedemptions(5).redemptionCount(5).active(true)
                .build();
        given(promoCodeRepository.findByCodeForUpdate("MAXED")).willReturn(Optional.of(promo));

        assertThatThrownBy(() -> pricingService.applyPromoCode("MAXED", BigDecimal.TEN))
                .isInstanceOf(PromoCodeNotApplicableException.class);
    }

    @Test
    void previewPromoCode_validCode_computesDiscountWithoutIncrementingRedemption() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("SAVE20").discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("20.00"))
                .validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600))
                .maxRedemptions(null).redemptionCount(3).active(true)
                .build();
        given(promoCodeRepository.findByCode("SAVE20")).willReturn(Optional.of(promo));

        PromoCodeApplication application = pricingService.previewPromoCode("SAVE20", new BigDecimal("100.00"));

        assertThat(application.discountAmount()).isEqualByComparingTo("20.00");
        assertThat(promo.getRedemptionCount()).isEqualTo(3);
        verify(promoCodeRepository, never()).findByCodeForUpdate(any());
    }

    @Test
    void previewPromoCode_whenCodeUnknown_throwsPromoCodeNotFoundException() {
        pricingService = service(PROPERTIES);
        given(promoCodeRepository.findByCode("NOPE")).willReturn(Optional.empty());

        assertThatThrownBy(() -> pricingService.previewPromoCode("NOPE", BigDecimal.TEN))
                .isInstanceOf(PromoCodeNotFoundException.class);
    }

    @Test
    void previewPromoCode_whenFullyRedeemed_throwsPromoCodeNotApplicableException() {
        pricingService = service(PROPERTIES);
        PromoCode promo = PromoCode.builder()
                .id(1L).code("MAXED").discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .validFrom(Instant.now().minusSeconds(3600))
                .validTo(Instant.now().plusSeconds(3600))
                .maxRedemptions(5).redemptionCount(5).active(true)
                .build();
        given(promoCodeRepository.findByCode("MAXED")).willReturn(Optional.of(promo));

        assertThatThrownBy(() -> pricingService.previewPromoCode("MAXED", BigDecimal.TEN))
                .isInstanceOf(PromoCodeNotApplicableException.class);
    }
}
