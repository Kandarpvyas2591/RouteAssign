package com.routeassign.repository;

import com.routeassign.domain.entity.UserAuth;
import com.routeassign.domain.entity.UserDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDetailsRepository extends JpaRepository<UserDetails, Long> {

    Optional<UserDetails> findByAuth(UserAuth auth);

    Optional<UserDetails> findByAuth_UserId(Long authId);

    /**
     * Finds all active, available delivery partners whose remaining capacity
     * is sufficient for the given order weight.
     * Used as the first filter pass in the assignment algorithm.
     *
     * remainingCapacity = capacity - currentAssignedWeight >= orderWeight
     */
    @Query("""
            SELECT ud FROM UserDetails ud
            WHERE ud.isActive = true
              AND ud.isAvailable = true
              AND (ud.capacity - ud.currentAssignedWeight) >= :orderWeight
              AND ud.auth.role = 'DELIVERY_PARTNER'
            """)
    List<UserDetails> findEligibleDeliveryPartners(@Param("orderWeight") Double orderWeight);

    /**
     * Finds all active delivery partners (regardless of availability/capacity).
     * Used for administrative queries.
     */
    @Query("""
            SELECT ud FROM UserDetails ud
            WHERE ud.isActive = true
              AND ud.auth.role = 'DELIVERY_PARTNER'
            """)
    List<UserDetails> findAllActiveDeliveryPartners();

    /**
     * Finds active delivery partners who are currently BUSY (isAvailable = false)
     * but still have remaining capacity for the new order weight.
     *
     * These are candidates for cross-vendor busy-partner reuse.
     * The assignment algorithm will compute a rest-adjusted ETA for them and
     * compare it against free partners before making a final selection.
     *
     * remainingCapacity = capacity - currentAssignedWeight >= orderWeight
     */
    @Query("""
            SELECT ud FROM UserDetails ud
            WHERE ud.isActive = true
              AND ud.isAvailable = false
              AND ud.capacity IS NOT NULL
              AND ud.currentAssignedWeight IS NOT NULL
              AND (ud.capacity - ud.currentAssignedWeight) >= :orderWeight
              AND ud.auth.role = 'DELIVERY_PARTNER'
            """)
    List<UserDetails> findBusyEligibleDeliveryPartners(@Param("orderWeight") Double orderWeight);
}
