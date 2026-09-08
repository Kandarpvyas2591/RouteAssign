package com.routeassign.domain.enums;

/**
 * Represents the status of a Delivery Assignment.
 */
public enum DeliveryStatus {

    /** Assignment created, partner not yet started. */
    ASSIGNED,

    /** Delivery partner is on the way to the vendor. */
    EN_ROUTE_TO_VENDOR,

    /** Delivery partner has arrived at the vendor and collected the order. */
    COLLECTED,

    /** Delivery partner is travelling to the customer. */
    EN_ROUTE_TO_CUSTOMER,

    /** Delivery successfully completed. */
    DELIVERED,

    /** Assignment was cancelled. */
    CANCELLED,

    /** Delivery failed (e.g. customer unreachable). */
    FAILED
}
