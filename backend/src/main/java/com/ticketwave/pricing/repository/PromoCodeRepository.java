package com.ticketwave.pricing.repository;

import com.ticketwave.pricing.entity.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCode(String code);

    /**
     * Row-locks the promo code for the duration of the caller's transaction,
     * so two concurrent redemptions can't both read the same redemptionCount
     * and both pass a max_redemptions check that only one of them should.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE p.code = :code")
    Optional<PromoCode> findByCodeForUpdate(@Param("code") String code);
}
