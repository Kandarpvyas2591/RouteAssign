package com.routeassign.service.algorithm.impl;

import com.routeassign.config.BusinessRulesConfig;
import com.routeassign.service.algorithm.DeliveryTimeAlgorithmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Algorithm 3 — Delivery time / days calculation.
 *
 * Working window : workingHourStart (10) – workingHourEnd (20)  every day.
 * Cut-off rule   : after cutoffHour (17) AND Home→Vendor > 30 km → next working day.
 * Distance mapping: 1 km ≈ 2 minutes of travel time (0.033 hours/km).
 *                   This is a configurable assumption — adjust HOURS_PER_KM as needed.
 */
@Service
@RequiredArgsConstructor
public class DeliveryTimeAlgorithmServiceImpl implements DeliveryTimeAlgorithmService {

    /**
     * Estimated travel time per kilometre expressed in hours.
     * 2 min/km  →  2/60 = 0.0333 h/km
     * Adjust this value to tune ETA accuracy for your geography.
     */
    private static final double HOURS_PER_KM = 2.0 / 60.0;

    private final BusinessRulesConfig businessRules;

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public LocalDateTime calculateExpectedDeliveryTime(
            LocalDateTime assignmentTime,
            double distancePartnerToVendor,
            double distanceVendorToCustomer) {

        // Step 1: determine the effective moment the partner starts travelling
        LocalDateTime workStart = determineWorkStartTime(assignmentTime, distancePartnerToVendor);

        // Step 2: calculate total travel hours for the full journey
        double travelToVendorHours   = distancePartnerToVendor  * HOURS_PER_KM;
        double travelToCustomerHours = distanceVendorToCustomer * HOURS_PER_KM;
        double totalTravelHours      = travelToVendorHours + travelToCustomerHours;

        // Step 3: schedule that travel time within the working window,
        //         spilling over to the next working day if needed.
        return scheduleWithinWorkingHours(workStart, totalTravelHours);
    }

    @Override
    public LocalDateTime determineWorkStartTime(LocalDateTime assignmentTime,
                                                double distancePartnerToVendor) {
        int assignmentHour   = assignmentTime.getHour();
        int cutoffHour       = businessRules.getCutoffHour();
        int workingHourStart = businessRules.getWorkingHourStart();
        int workingHourEnd   = businessRules.getWorkingHourEnd();
        double maxDistAfter5 = businessRules.getAfter5PmMaxDistanceKm();

        // ── Before the working day starts ────────────────────────────────────
        if (assignmentHour < workingHourStart) {
            return atWorkStart(assignmentTime.toLocalDate());
        }

        // ── After working day ends (≥ 20:00) ─────────────────────────────────
        if (assignmentHour >= workingHourEnd) {
            return atWorkStart(assignmentTime.toLocalDate().plusDays(1));
        }

        // ── After cut-off (≥ 17:00) ───────────────────────────────────────────
        if (assignmentHour >= cutoffHour) {
            if (distancePartnerToVendor > maxDistAfter5) {
                // Rule §10: Home→Vendor > 30 km after 5 PM → start next working day
                return atWorkStart(assignmentTime.toLocalDate().plusDays(1));
            }
            // Home→Vendor ≤ 30 km after 5 PM → can still start today
            return assignmentTime;
        }

        // ── Normal slot: before cut-off, within working hours ─────────────────
        return assignmentTime;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Busy-partner ETA (cross-vendor reuse)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calculates the expected delivery time for a partner who is currently busy
     * on a different vendor's order.
     *
     * Steps:
     *  1. Partner finishes current delivery at {@code currentDeliveryEta}.
     *  2. Partner travels home and rests for {@code restDurationMinutes}.
     *     → restReadyTime = currentDeliveryEta + restDurationMinutes
     *  3. Snap restReadyTime to the next valid working window:
     *       - If restReadyTime is before 10 AM  → start at 10 AM same day
     *       - If restReadyTime is after  8 PM   → start at 10 AM next day
     *       - If restReadyTime is after  5 PM AND Home→Vendor > 30 km
     *                                           → start at 10 AM next day
     *       - Otherwise                         → start at restReadyTime
     *  4. Schedule (Home→Vendor + Vendor→Customer) travel within working hours
     *     from that start time, spilling to the next day if needed.
     */
    @Override
    public LocalDateTime calculateEtaForBusyPartner(
            LocalDateTime currentDeliveryEta,
            int           restDurationMinutes,
            double        distancePartnerToVendor,
            double        distanceVendorToCustomer) {

        // Step 1 + 2: when the partner is rested and ready to leave home
        LocalDateTime restReadyTime = currentDeliveryEta.plusMinutes(restDurationMinutes);

        // Step 3: snap to the next valid working window using the existing rule logic
        LocalDateTime workStart = determineWorkStartTime(restReadyTime, distancePartnerToVendor);

        // Step 4: schedule total travel within working hours
        double travelHours = (distancePartnerToVendor + distanceVendorToCustomer) * HOURS_PER_KM;
        return scheduleWithinWorkingHours(workStart, travelHours);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Schedules {@code hoursNeeded} of working time starting from {@code start},
     * respecting the 10 AM – 8 PM working window.
     * If the remaining window today is insufficient the work spills into the
     * next day (and further days if needed).
     */
    private LocalDateTime scheduleWithinWorkingHours(LocalDateTime start, double hoursNeeded) {
        int workingHourEnd   = businessRules.getWorkingHourEnd();
        int workingHourStart = businessRules.getWorkingHourStart();
        int dailyWindowHours = workingHourEnd - workingHourStart;   // = 10 h

        LocalDateTime cursor = start;
        double remaining     = hoursNeeded;

        while (remaining > 0) {
            // How many hours are left in the working window today from cursor?
            double endOfDayDecimal    = workingHourEnd;
            double cursorDecimal      = cursor.getHour() + cursor.getMinute() / 60.0
                                        + cursor.getSecond() / 3600.0;
            double availableToday     = endOfDayDecimal - cursorDecimal;

            if (availableToday <= 0) {
                // Cursor is already past end-of-day; move to next day
                cursor    = atWorkStart(cursor.toLocalDate().plusDays(1));
                remaining -= 0;   // no progress today
                continue;
            }

            if (remaining <= availableToday) {
                // All remaining hours fit in today's window
                long totalMinutes = Math.round(remaining * 60);
                cursor = cursor.plusMinutes(totalMinutes);
                remaining = 0;
            } else {
                // Consume the rest of today and continue tomorrow
                remaining -= availableToday;
                cursor     = atWorkStart(cursor.toLocalDate().plusDays(1));
            }

            // Safety guard — prevent infinite loop if dailyWindowHours == 0
            if (dailyWindowHours <= 0) break;
        }

        return cursor;
    }

    /** Returns the work-start time (10:00 AM) on the given date. */
    private LocalDateTime atWorkStart(LocalDate date) {
        return LocalDateTime.of(date, LocalTime.of(businessRules.getWorkingHourStart(), 0));
    }
}
