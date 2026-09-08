package com.routeassign.domain.entity;

import com.routeassign.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an order placed by a customer from a specific vendor.
 * The delivery location coordinates on this entity are the authoritative
 * destination used by the assignment algorithm.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private CustomerDetails customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorDetails vendor;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, length = 30)
    private OrderStatus orderStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Latitude of the delivery destination provided at order placement.
     */
    @Column(name = "delivery_location_latitude", nullable = false)
    private Double deliveryLocationLatitude;

    /**
     * Longitude of the delivery destination provided at order placement.
     */
    @Column(name = "delivery_location_longitude", nullable = false)
    private Double deliveryLocationLongitude;

    /**
     * Pre-computed total weight of all order items (kg).
     * Stored here so the assignment algorithm does not need to re-sum items each time.
     */
    @Column(name = "total_weight", nullable = false)
    private Double totalWeight;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.orderStatus == null) {
            this.orderStatus = OrderStatus.PENDING;
        }
    }
}
