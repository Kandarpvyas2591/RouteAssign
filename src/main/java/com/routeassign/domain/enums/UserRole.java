package com.routeassign.domain.enums;

/**
 * Roles that a User_Auth account can have in the system.
 */
public enum UserRole {

    /** Regular end-user / customer. */
    CUSTOMER,

    /** Delivery partner who fulfils deliveries. */
    DELIVERY_PARTNER,

    /** Vendor who lists items and accepts orders. */
    VENDOR,

    /** System administrator. */
    ADMIN
}
