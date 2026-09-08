package com.routeassign.domain.entity;

import com.routeassign.domain.enums.HistoryStatus;
import jakarta.persistence.*;
import lombok.*;

/**
 * History record for vendor-related order events.
 * Maintained separately from HistoryDeliveryPartner so vendor and delivery-partner
 * history can evolve independently.
 */
@Entity
@Table(name = "history_vendor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryVendor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorDetails vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HistoryStatus status;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @PrePersist
    protected void onCreate() {
        if (this.isActive == null) this.isActive = true;
    }
}
