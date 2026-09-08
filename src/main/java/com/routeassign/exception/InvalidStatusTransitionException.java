package com.routeassign.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an illegal status transition is attempted
 * (e.g. moving an order from DELIVERED back to PENDING,
 *  or a delivery assignment from CANCELLED to COLLECTED).
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }

    public InvalidStatusTransitionException(String entityName, Object fromStatus, Object toStatus) {
        super(String.format(
                "Invalid status transition for %s: cannot move from '%s' to '%s'.",
                entityName, fromStatus, toStatus));
    }
}
