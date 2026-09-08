package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents the inventory relationship between a vendor and an item.
 * A vendor can stock many items; each Store record holds the available quantity.
 */
@Entity
@Table(name = "store",
       uniqueConstraints = @UniqueConstraint(columnNames = {"vendor_id", "item_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Store {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private VendorDetails vendor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
