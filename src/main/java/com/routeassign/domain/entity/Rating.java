package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a single rating entry.
 * Referenced by both VendorDetails and HistoryDeliveryPartner.
 */
@Entity
@Table(name = "rating")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rating_id")
    private Long ratingId;

    /**
     * Numeric rating value (e.g. 1.0 – 5.0).
     */
    @Column(name = "rating", nullable = false)
    private Double rating;
}
