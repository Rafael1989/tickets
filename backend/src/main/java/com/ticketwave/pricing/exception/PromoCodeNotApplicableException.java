package com.ticketwave.pricing.exception;

import com.ticketwave.common.exception.TicketwaveException;
import org.springframework.http.HttpStatus;

public class PromoCodeNotApplicableException extends TicketwaveException {

    public PromoCodeNotApplicableException(String code, String reason) {
        super(HttpStatus.CONFLICT, "PROMO_CODE_NOT_APPLICABLE", "Promo code '" + code + "' is " + reason);
    }
}
