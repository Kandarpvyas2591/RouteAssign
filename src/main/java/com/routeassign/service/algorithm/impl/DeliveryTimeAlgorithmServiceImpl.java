package com.routeassign.service.algorithm.impl;

import com.routeassign.domain.enums.AssignmentRuleKey;
import com.routeassign.service.AssignmentRuleService;
import com.routeassign.service.algorithm.DeliveryTimeAlgorithmService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Algorithm 3 — Delivery time / days calculation.
 *
 * Working window : WORKING_HOUR_START – WORKING_HOUR_END  (read from DB at call time).
 * Cut-off rule   : after LATE_ASSIGNMENT_HOUR AND Home→Vendor > HOME_VENDOR_MAX_DISTANCE
 *                  → next working day.
 * Distance mapping: 1 km ≈ 2 minutes of travel time (0.033 hours/km).
 *                   HOURS_PER_KM is a technical constant, not a business rule.
 */
@Service
@RequiredArgsConstructor
public class DeliveryTimeAlgorithmServiceImpl implements DeliveryTimeAlgorithmService {

    /**
     * Estimated travel time per kilometre expressed in hours.
     * 2 min/km  →  2/60 = 0.0333 h/km
     *
     * This is a fixed technical assumption about average vehicle speed, not a
     * business rule — it does not belong in the assignment_rules table.
     */
    private static final double HOURS_PER_KM = 2.0 / 60.0;

    private final AssignmentRuleService assignmentRuleService;

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
        // Read all needed rules from DB — source of truth is MySQL, not application.properties
        int    workingHourStart = assignmentRuleService.getIntegerRule(AssignmentRuleKey.WORKING_HOUR_START);
        int    workingHourEnd   = assignmentRuleService.getIntegerRule(AssignmentRuleKey.WORKING_HOUR_END);
        int    lateHour         = assignmentRuleService.getIntegerRule(AssignmentRuleKey.LATE_ASSIGNMENT_HOUR);
        double maxDistAfterLate = assignmentRuleService.getDoubleRule(AssignmentRuleKey.HOME_VENDOR_MAX_DISTANCE);

        int assignmentHour = assignmentTime.getHour();

        // ── Before the working day starts ────────────────────────────────────
        if (assignmentHour < workingHourStart) {
            return atWorkStart(assignmentTime.toLocalDate(), workingHourStart);
        }

        // ── After working day ends ────────────────────────────────────────────
        if (assignmentHour >= workingHourEnd) {
            return atWorkStart(assignmentTime.toLocalDate().plusDays(1), workingHourStart);
        }

        // ── After late cut-off ────────────────────────────────────────────────
        if (assignmentHour >= lateHour) {
            if (distancePartnerToVendor > maxDistAfterLate) {
                // Rule: Home→Vendor > threshold after cut-off → start next working day
                return atWorkStart(assignmentTime.toLocalDate().plusDays(1), workingHourStart);
            }
            // Home→Vendor ≤ threshold after cut-off → can still start today
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
     *  2. Partner rests for {@code restDurationMinutes}.
     *     → restReadyTime = currentDeliveryEta + restDurationMinutes
     *  3. Snap restReadyTime to the next valid working window.
     *  4. Schedule (Home→Vendor + Vendor→Customer) travel from that start time.
     */
    @Override
    public LocalDateTime calculateEtaForBusyPartner(
            LocalDateTime currentDeliveryEta,
            int           restDurationMinutes,
            double        distancePartnerToVendor,
            double        distanceVendorToCustomer) {

        LocalDateTime restReadyTime = currentDeliveryEta.plusMinutes(restDurationMinutes);
        LocalDateTime workStart     = determineWorkStartTime(restReadyTime, distancePartnerToVendor);

        double travelHours = (distancePartnerToVendor + distanceVendorToCustomer) * HOURS_PER_KM;
        return scheduleWithinWorkingHours(workStart, travelHours);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Schedules {@code hoursNeeded} of working time starting from {@code start},
     * respecting the DB-driven working window.
     * Spills into the next day (and further) if today's remaining window is insufficient.
     */
    private LocalDateTime scheduleWithinWorkingHours(LocalDateTime start, double hoursNeeded) {
        int workingHourEnd   = assignmentRuleService.getIntegerRule(AssignmentRuleKey.WORKING_HOUR_END);
        int workingHourStart = assignmentRuleService.getIntegerRule(AssignmentRuleKey.WORKING_HOUR_START);
        int dailyWindowHours = workingHourEnd - workingHourStart;

        LocalDateTime cursor  = start;
        double        remaining = hoursNeeded;

        while (remaining > 0) {
            double endOfDayDecimal = workingHourEnd;
            double cursorDecimal   = cursor.getHour()
                                   + cursor.getMinute()   / 60.0
                                   + cursor.getSecond()   / 3600.0;
            double availableToday  = endOfDayDecimal - cursorDecimal;

            if (availableToday <= 0) {
                cursor    = atWorkStart(cursor.toLocalDate().plusDays(1), workingHourStart);
                continue;
            }

            if (remaining <= availableToday) {
                long totalMinutes = Math.round(remaining * 60);
                cursor    = cursor.plusMinutes(totalMinutes);
                remaining = 0;
            } else {
                remaining -= availableToday;
                cursor     = atWorkStart(cursor.toLocalDate().plusDays(1), workingHourStart);
            }

            // Safety guard — prevent infinite loop if dailyWindowHours == 0
            if (dailyWindowHours <= 0) break;
        }

        return cursor;
    }

    /** Returns the work-start time on the given date using the DB-configured start hour. */
    private LocalDateTime atWorkStart(LocalDate date, int workingHourStart) {
        return LocalDateTime.of(date, LocalTime.of(workingHourStart, 0));
    }
}
