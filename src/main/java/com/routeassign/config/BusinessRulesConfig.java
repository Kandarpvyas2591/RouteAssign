package com.routeassign.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalised business rules for the assignment algorithm.
 *
 * All values are loaded from application.properties under the prefix
 * {@code app.business} so they can be changed without recompiling.
 *
 * ┌────────────────────────────────────────────────────────────────────────────┐
 * │ Property key                                 │ Default  │ Section in spec  │
 * ├────────────────────────────────────────────────────────────────────────────┤
 * │ app.business.max-additional-delivery-distance│ 10.0 km  │ §6  (configurable│
 * │                                              │          │  threshold)      │
 * │ app.business.after-5pm-max-distance-km       │ 30.0 km  │ §9 / §10         │
 * │ app.business.working-hour-start              │ 10       │ §8               │
 * │ app.business.working-hour-end                │ 20       │ §8               │
 * │ app.business.cutoff-hour                     │ 17       │ §9               │
 * └────────────────────────────────────────────────────────────────────────────┘
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.business")
public class BusinessRulesConfig {

    /**
     * §6 — MAX_ADDITIONAL_DELIVERY_DISTANCE (km).
     *
     * The maximum extra distance a same-vendor delivery partner may travel to
     * the new customer location before the system stops reusing that partner
     * and falls back to the full partner-selection algorithm.
     *
     * This value is intentionally configurable and has no hardcoded default
     * in the algorithm itself; it must be set in application.properties.
     */
    private double maxAdditionalDeliveryDistance = 10.0;

    /**
     * §9 / §10 — After-5-PM distance threshold (km).
     *
     * If the assignment runs after {@code cutoffHour} AND the distance from the
     * delivery partner's home to the vendor exceeds this value, the delivery
     * work is deferred to the next working day.
     *
     * Fixed business rule per spec §10: 30 km.
     */
    private double after5PmMaxDistanceKm = 30.0;

    /**
     * §8 — Start of the working day (24-hour clock, inclusive).
     * Default: 10  (10:00 AM)
     */
    private int workingHourStart = 10;

    /**
     * §8 — End of the working day (24-hour clock, exclusive).
     * Default: 20  (8:00 PM)
     */
    private int workingHourEnd = 20;

    /**
     * §9 — Cut-off hour after which the after-5-PM distance rules are applied
     * (24-hour clock).
     * Default: 17  (5:00 PM)
     */
    private int cutoffHour = 17;
}
