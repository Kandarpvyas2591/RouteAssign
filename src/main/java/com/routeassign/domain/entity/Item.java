package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a product/item that can be stocked by a vendor and ordered by a customer.
 */
@Entity
@Table(name = "item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Weight of the item in kilograms.
     * Used by the capacity algorithm to compute total order weight.
     */
    @Column(name = "weight", nullable = false)
    private Double weight;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
