package com.ticketwave.pricing.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class DuplicatePromoCodeException extends TicketwaveException {

    public DuplicatePromoCodeException(String code) {
        super(HttpStatus.CONFLICT, "DUPLICATE_PROMO_CODE", "Promo code '" + code + "' is already taken");
    }
}
