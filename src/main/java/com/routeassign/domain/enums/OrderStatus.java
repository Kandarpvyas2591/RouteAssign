package com.routeassign.domain.enums;

/**
 * Represents the lifecycle status of an Order.
 */
public enum OrderStatus {

    /** Order has been placed but not yet assigned to a delivery partner. */
    PENDING,

    /** Order has been assigned to a delivery partner. */
    ASSIGNED,

    /** Delivery partner has picked up the order from the vendor. */
    PICKED_UP,

    /** Order is currently being delivered. */
    IN_TRANSIT,

    /** Order has been successfully delivered to the customer. */
    DELIVERED,

    /** Order was cancelled before delivery. */
    CANCELLED
}
