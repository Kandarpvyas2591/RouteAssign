package com.routeassign.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores customer-specific profile and default delivery location.
 * Linked 1-to-1 with UserAuth (CUSTOMER role).
 */
@Entity
@Table(name = "customer_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDetails {

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
     * Default delivery latitude.
     * Orders may override this with a specific delivery location.
     */
    @Column(name = "latitude")
    private Double latitude;

    /**
     * Default delivery longitude.
     * Orders may override this with a specific delivery location.
     */
    @Column(name = "longitude")
    private Double longitude;
}
