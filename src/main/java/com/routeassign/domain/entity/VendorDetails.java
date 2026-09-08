package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores vendor-specific profile, location, and rating.
 * Linked 1-to-1 with UserAuth (VENDOR role).
 */
@Entity
@Table(name = "vendor_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VendorDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auth_id", nullable = false, unique = true)
    private UserAuth auth;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rating_id")
    private Rating rating;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @PrePersist
    protected void onCreate() {
        if (this.isActive == null) this.isActive = true;
    }
}
