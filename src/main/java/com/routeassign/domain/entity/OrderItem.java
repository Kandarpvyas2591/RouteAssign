package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Line item of an Order. Links an Order to an Item with quantity and recorded weight.
 */
@Entity
@Table(name = "order_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /**
     * Weight per unit (kg) captured at order placement time.
     * Stored separately so that changes to Item.weight do not retroactively
     * affect historical orders.
     */
    @Column(name = "weight", nullable = false)
    private Double weight;
}
