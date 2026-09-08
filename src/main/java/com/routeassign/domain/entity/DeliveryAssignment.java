package com.routeassign.domain.entity;

import com.routeassign.domain.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Represents the current, active assignment of a delivery partner to an order.
 * This is distinct from the history record — it tracks live assignment state.
 *
 * The assignment algorithm reads and writes this entity to:
 *  - determine which partners are already assigned to which vendors
 *  - calculate current assigned weight per partner
 *  - check same-vendor reuse eligibility
 */
@Entity
@Table(name = "delivery_assignment")
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_partner_id", nullable = false)
    private UserDetails deliveryPartner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorDetails vendor;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    /**
     * Calculated expected delivery date/time based on distance and working-hour rules.
     */
    @Column(name = "expected_delivery_time")
    private LocalDateTime expectedDeliveryTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private DeliveryStatus deliveryStatus;

    /**
     * Distance (km) from delivery partner home to vendor. Stored for audit/reporting.
     */
    @Column(name = "distance_partner_to_vendor")
    private Double distancePartnerToVendor;

    /**
     * Distance (km) from vendor to customer delivery location. Stored for audit/reporting.
     */
    @Column(name = "distance_vendor_to_customer")
    private Double distanceVendorToCustomer;

    @PrePersist
    protected void onCreate() {
        this.assignedAt = LocalDateTime.now();
        if (this.deliveryStatus == null) {
            this.deliveryStatus = DeliveryStatus.ASSIGNED;
        }
    }
}
