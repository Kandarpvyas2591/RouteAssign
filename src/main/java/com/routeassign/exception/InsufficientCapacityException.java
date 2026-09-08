package com.routeassign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by the Capacity Algorithm when no delivery partner has enough
 * remaining capacity to accept the new order weight.
 * Maps to HTTP 422 Unprocessable Entity.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientCapacityException extends RuntimeException {

    public InsufficientCapacityException(String message) {
        super(message);
    }

    public InsufficientCapacityException(double required, double available) {
        super(String.format(
                "Insufficient capacity: order requires %.2f kg but only %.2f kg is available.",
                required, available));
    }
}
