package com.routeassign.domain.entity;

import com.routeassign.domain.enums.HistoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Immutable history record created when a delivery assignment is completed, cancelled, or failed.
 *
 * The {@code completedAt} field is critical for the assignment algorithm:
 * it is used to determine idle time (current time − completedAt) when
 * choosing between equally-rated delivery partners.
 */
@Entity
@Table(name = "history_delivery_partner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoryDeliveryPartner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_partner_id", nullable = false)
    private UserDetails deliveryPartner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HistoryStatus status;

    /**
     * Timestamp when the delivery was completed (or failed/cancelled).
     * Used by the idle-time tie-breaking rule in the assignment algorithm.
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @PrePersist
    protected void onCreate() {
        if (this.isActive == null) this.isActive = true;
    }
}
