package com.routeassign.service.algorithm;

import java.time.LocalDateTime;

/**
 * Algorithm 3 — Delivery Time / Days Calculation.
 *
 * Determines the expected delivery date-time for an order given:
 *  - The time the assignment algorithm runs
 *  - The distance from the delivery partner's home to the vendor
 *  - The distance from the vendor to the customer
 *  - Working-hour constraints (10:00 AM – 8:00 PM)
 *  - After-5-PM rule  (if Home→Vendor > 30 km, start next working day)
 *  - Non-working time is never counted as delivery working time
 */
public interface DeliveryTimeAlgorithmService {

    /**
     * Calculates the expected delivery date-time.
     *
     * Rules applied:
     *  1. Assignment after 5 PM AND Home→Vendor > 30 km → work starts next day at 10 AM.
     *  2. Assignment after 5 PM AND Home→Vendor ≤ 30 km → current day may still be used
     *     provided sufficient working hours remain before 8 PM.
     *  3. Available working window is always 10 AM – 8 PM.
     *  4. If remaining window on current day is insufficient, overflow to next day.
     *  5. The mapping of distance → working hours/days is driven by a configurable
     *     business rule (not hardcoded in this interface).
     *
     * @param assignmentTime         the exact time the assignment algorithm runs
     * @param distancePartnerToVendor distance (km) from partner home to vendor
     * @param distanceVendorToCustomer distance (km) from vendor to customer
     * @return the expected delivery LocalDateTime
     */
    LocalDateTime calculateExpectedDeliveryTime(
            LocalDateTime assignmentTime,
            double distancePartnerToVendor,
            double distanceVendorToCustomer);

    /**
     * Determines the effective work-start time given the assignment time and distance rules.
     *
     * @param assignmentTime          the time the assignment runs
     * @param distancePartnerToVendor distance (km) from partner home to vendor
     * @return the LocalDateTime from which delivery work can begin
     */
    LocalDateTime determineWorkStartTime(LocalDateTime assignmentTime, double distancePartnerToVendor);
}
