package com.ticketwave.pricing.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PromoCodeTest {

    @Test
    void onCreate_whenFieldsUnset_defaultsCreatedAtRedemptionCountAndActive() {
        PromoCode promoCode = PromoCode.builder().build();

        promoCode.onCreate();

        assertThat(promoCode.getCreatedAt()).isNotNull();
        assertThat(promoCode.getCreatedAt()).isBeforeOrEqualTo(Instant.now());
        assertThat(promoCode.getRedemptionCount()).isZero();
        assertThat(promoCode.getActive()).isTrue();
    }

    @Test
    void onCreate_whenFieldsAlreadySet_leavesThemUnchanged() {
        Instant explicit = Instant.parse("2020-01-01T00:00:00Z");
        PromoCode promoCode = PromoCode.builder()
                .createdAt(explicit)
                .redemptionCount(5)
                .active(false)
                .build();

        promoCode.onCreate();

        assertThat(promoCode.getCreatedAt()).isEqualTo(explicit);
        assertThat(promoCode.getRedemptionCount()).isEqualTo(5);
        assertThat(promoCode.getActive()).isFalse();
    }
}
