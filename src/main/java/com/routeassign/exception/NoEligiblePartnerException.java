package com.routeassign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by the Partner Selection Algorithm when no delivery partner passes
 * all eligibility checks (active, available, sufficient capacity, distance).
 * Maps to HTTP 422 Unprocessable Entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class NoEligiblePartnerException extends RuntimeException {

    public NoEligiblePartnerException() {
        super("No eligible delivery partner is available to fulfil this order at this time.");
    }

    public NoEligiblePartnerException(String message) {
        super(message);
    }
}
