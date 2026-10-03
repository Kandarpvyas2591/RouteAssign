package com.routeassign.config;

/**
 * Compile-time constants shared across the application.
 *
 * Only values that are truly fixed (mathematical, structural, or
 * infrastructure-level) belong here.
 *
 * Business-configurable rules (distances, working hours, rest durations, etc.)
 * are stored in the {@code assignment_rules} database table and accessed via
 * {@code AssignmentRuleService}. Do not add business rules here.
 */
public final class AppConstants {

    private AppConstants() {}

    // ── Earth / Geography ─────────────────────────────────────────────────────

    /**
     * Earth's mean radius used by the Haversine formula (km).
     * This is a mathematical constant — not a business rule.
     */
    public static final double EARTH_RADIUS_KM = 6371.0;

    // ── API paths ─────────────────────────────────────────────────────────────

    public static final String API_BASE_PATH = "/api";

    public static final String AUTH_PATH             = API_BASE_PATH + "/auth";
    public static final String USERS_PATH            = API_BASE_PATH + "/users";
    public static final String CUSTOMERS_PATH        = API_BASE_PATH + "/customers";
    public static final String VENDORS_PATH          = API_BASE_PATH + "/vendors";
    public static final String ITEMS_PATH            = API_BASE_PATH + "/items";
    public static final String ORDERS_PATH           = API_BASE_PATH + "/orders";
    public static final String ASSIGNMENTS_PATH      = API_BASE_PATH + "/assignments";
    public static final String HISTORY_PATH          = API_BASE_PATH + "/history";
    public static final String ASSIGNMENT_RULES_PATH = API_BASE_PATH + "/v1/assignment-rules";

    // ── JWT ───────────────────────────────────────────────────────────────────

    public static final String TOKEN_PREFIX = "Bearer ";
    public static final String AUTH_HEADER  = "Authorization";
    public static final String TOKEN_TYPE   = "Bearer";

    // ── Roles ─────────────────────────────────────────────────────────────────

    public static final String ROLE_ADMIN            = "ADMIN";
    public static final String ROLE_VENDOR           = "VENDOR";
    public static final String ROLE_CUSTOMER         = "CUSTOMER";
    public static final String ROLE_DELIVERY_PARTNER = "DELIVERY_PARTNER";
}
