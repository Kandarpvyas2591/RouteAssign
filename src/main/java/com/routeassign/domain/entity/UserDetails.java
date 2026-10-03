package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores profile and location information for a user (delivery partner or customer).
 * Linked 1-to-1 with UserAuth.
 */
@Entity
@Table(name = "user_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auth_id", nullable = false, unique = true)
    private UserAuth auth;

    @Column(name = "mobile_no", nullable = false, length = 20)
    private String mobileNo;

    /**
     * Maximum carrying capacity in kilograms (applicable for delivery partners).
     */
    @Column(name = "capacity")
    private Double capacity;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /**
     * Whether this user account profile is active.
     * For delivery partners this also feeds into the eligibility check (is_active).
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * Whether the delivery partner is currently available to accept a new assignment.
     * Only meaningful for DELIVERY_PARTNER role.
     */
    @Column(name = "is_available")
    private Boolean isAvailable;

    /**
     * Cumulative weight (kg) of orders currently assigned to this delivery partner.
     * Used by the capacity algorithm: remainingCapacity = capacity - currentAssignedWeight.
     * Only meaningful for DELIVERY_PARTNER role.
     */
    @Column(name = "current_assigned_weight")
    private Double currentAssignedWeight;

    /**
     * Overall rating of the delivery partner (aggregated from completed deliveries).
     * Only meaningful for DELIVERY_PARTNER role.
     */
    @Column(name = "rating")
    private Double rating;

    /**
     * Optimistic locking version counter.
     *
     * Incremented by Hibernate on every UPDATE to this row.  If two transactions
     * both read the same version and one commits first, the other will receive a
     * {@link jakarta.persistence.OptimisticLockException} on flush, preventing
     * silent data corruption (e.g. two orders both adding weight simultaneously).
     *
     * This is the second layer of concurrency protection, complementing the
     * pessimistic row lock acquired in the final assignment step.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (this.isActive == null) this.isActive = true;
        if (this.isAvailable == null) this.isAvailable = true;
        if (this.currentAssignedWeight == null) this.currentAssignedWeight = 0.0;
    }
}
