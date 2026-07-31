package com.ticketwave.pricing.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PromoCodeNotFoundException extends TicketwaveException {

    public PromoCodeNotFoundException(String code) {
        super(HttpStatus.NOT_FOUND, "PROMO_CODE_NOT_FOUND", "Promo code '" + code + "' was not found");
    }
}
