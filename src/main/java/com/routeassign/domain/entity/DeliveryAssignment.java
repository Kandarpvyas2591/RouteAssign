package com.routeassign.domain.entity;

import com.routeassign.domain.enums.AssignmentFailureReason;
import com.routeassign.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents one assignment attempt for an order.
 *
 * An order may have multiple assignment rows — one per attempt:
 * <pre>
 * order_id=101, attempt=1, partner=25, status=REJECTED
 * order_id=101, attempt=2, partner=31, status=ACCEPTED → DELIVERED
 * </pre>
 *
 * The unique constraint on {@code order_id} has been removed to allow multiple
 * historical rows per order.  The active (current) assignment is determined by
 * querying for non-terminal statuses, not by uniqueness.
 *
 * Concurrency safety: {@link UserDetails#getVersion()} (optimistic lock) and the
 * PESSIMISTIC_WRITE lock in {@code DeliveryAssignmentServiceImpl} together prevent
 * two threads from creating conflicting active assignments for the same order.
 */
@Entity
@Table(
    name = "delivery_assignment",
    indexes = {
        @Index(name = "idx_da_order_id",   columnList = "order_id"),
        @Index(name = "idx_da_partner_id", columnList = "delivery_partner_id"),
        @Index(name = "idx_da_status",     columnList = "delivery_status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * The order this assignment attempt belongs to.
     * No longer unique — multiple attempts per order are allowed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_partner_id", nullable = false)
    private UserDetails deliveryPartner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorDetails vendor;

    /**
     * 1-based counter tracking how many times this order has been assigned.
     * Attempt 1 = initial assignment, 2+ = reassignments.
     */
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    /** When the partner accepted the assignment (status → ACCEPTED). */
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /** When the delivery was completed or definitively closed (DELIVERED / FAILED). */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Calculated expected delivery date/time based on distance and working-hour rules. */
    @Column(name = "expected_delivery_time")
    private LocalDateTime expectedDeliveryTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private DeliveryStatus deliveryStatus;

    /**
     * Why this attempt ended in a non-delivery outcome.
     * Null when the assignment is active or completed successfully.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private AssignmentFailureReason failureReason;

    /** Distance (km) from delivery partner home to vendor. Stored for audit. */
    @Column(name = "distance_partner_to_vendor")
    private Double distancePartnerToVendor;

    /** Distance (km) from vendor to customer delivery location. Stored for audit. */
    @Column(name = "distance_vendor_to_customer")
    private Double distanceVendorToCustomer;

    @PrePersist
    protected void onCreate() {
        this.assignedAt = LocalDateTime.now();
        if (this.deliveryStatus == null) {
            this.deliveryStatus = DeliveryStatus.ASSIGNED;
        }
        if (this.attemptNumber == null) {
            this.attemptNumber = 1;
        }
    }
}
