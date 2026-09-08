package com.routeassign.config;

/**
 * Compile-time constants shared across the application.
 *
 * Only values that are truly fixed (not environment-specific) belong here.
 * Tuneable business rules belong in {@link BusinessRulesConfig}.
 */
public final class AppConstants {

    private AppConstants() {}

    // ── Earth / Geography ─────────────────────────────────────────────────────

    /** Earth's mean radius used by the Haversine formula (km). */
    public static final double EARTH_RADIUS_KM = 6371.0;

    // ── Working Hours ─────────────────────────────────────────────────────────

    /**
     * §8 — Earliest hour of the working day (10:00 AM).
     * Mirrors the default in BusinessRulesConfig; kept here for use
     * in contexts where Spring injection is not available.
     */
    public static final int DEFAULT_WORKING_HOUR_START = 10;

    /**
     * §8 — Latest hour of the working day (8:00 PM → hour 20).
     */
    public static final int DEFAULT_WORKING_HOUR_END = 20;

    /**
     * §9 — Cut-off hour for the after-5-PM rule (5:00 PM → hour 17).
     */
    public static final int DEFAULT_CUTOFF_HOUR = 17;

    // ── Distance Rules ────────────────────────────────────────────────────────

    /**
     * §10 — Fixed 30 km threshold for the after-5-PM rule.
     * If Home→Vendor distance exceeds this after cut-off hour,
     * work starts the next working day.
     */
    public static final double AFTER_5PM_MAX_DISTANCE_KM = 30.0;

    // ── API ───────────────────────────────────────────────────────────────────

    public static final String API_BASE_PATH = "/api";

    public static final String AUTH_PATH       = API_BASE_PATH + "/auth";
    public static final String USERS_PATH      = API_BASE_PATH + "/users";
    public static final String CUSTOMERS_PATH  = API_BASE_PATH + "/customers";
    public static final String VENDORS_PATH    = API_BASE_PATH + "/vendors";
    public static final String ITEMS_PATH      = API_BASE_PATH + "/items";
    public static final String ORDERS_PATH     = API_BASE_PATH + "/orders";
    public static final String ASSIGNMENTS_PATH = API_BASE_PATH + "/assignments";
    public static final String HISTORY_PATH    = API_BASE_PATH + "/history";

    // ── JWT ───────────────────────────────────────────────────────────────────

    public static final String TOKEN_PREFIX       = "Bearer ";
    public static final String AUTH_HEADER        = "Authorization";
    public static final String TOKEN_TYPE         = "Bearer";

    // ── Roles ─────────────────────────────────────────────────────────────────

    public static final String ROLE_ADMIN            = "ADMIN";
    public static final String ROLE_VENDOR           = "VENDOR";
    public static final String ROLE_CUSTOMER         = "CUSTOMER";
    public static final String ROLE_DELIVERY_PARTNER = "DELIVERY_PARTNER";
}
