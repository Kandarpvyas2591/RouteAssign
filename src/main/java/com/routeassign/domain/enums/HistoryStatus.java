package com.routeassign.domain.enums;

/**
 * Status values used in History records (delivery partner history, vendor history).
 */
public enum HistoryStatus {

    /** The associated order/delivery was completed successfully. */
    COMPLETED,

    /** The associated order/delivery was cancelled. */
    CANCELLED,

    /** The delivery failed (e.g. customer not available). */
    FAILED
}
