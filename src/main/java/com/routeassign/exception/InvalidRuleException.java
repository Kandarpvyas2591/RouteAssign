package com.routeassign.exception;

/**
 * Thrown when an assignment rule value fails domain validation.
 *
 * Examples:
 *  - WORKING_HOUR_START = 25  (hour out of [0, 23])
 *  - REST_DURATION_MINUTES = -50  (negative duration)
 *  - HOME_VENDOR_MAX_DISTANCE = "abc"  (not parseable as DOUBLE)
 *  - WORKING_HOUR_START >= WORKING_HOUR_END  (logical contradiction)
 */
public class InvalidRuleException extends RuntimeException {

    public InvalidRuleException(String message) {
        super(message);
    }

    public InvalidRuleException(String ruleKey, String reason) {
        super("Invalid value for rule '" + ruleKey + "': " + reason);
    }
}
