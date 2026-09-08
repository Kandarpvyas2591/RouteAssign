package com.routeassign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a vendor does not have enough stock to fulfil an order item.
 * Maps to HTTP 422 Unprocessable Entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }

    public InsufficientStockException(String itemName, int requested, int available) {
        super(String.format(
                "Insufficient stock for item '%s': requested %d but only %d available.",
                itemName, requested, available));
    }
}
