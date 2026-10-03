package com.routeassign.repository;

import com.routeassign.domain.entity.UserAuth;
import com.routeassign.domain.entity.UserDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
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

    /**
     * Acquires a PESSIMISTIC_WRITE (SELECT … FOR UPDATE) lock on a single
     * delivery partner row.
     *
     * Called immediately before the final assignment step so that only one
     * transaction at a time can read-validate-write a partner's capacity and
     * availability state.  The lock is held until the surrounding
     * {@code @Transactional} method commits or rolls back.
     *
     * Pattern:
     * <pre>
     * userDetailsRepository.findByIdWithPessimisticLock(partnerId)
     *     .ifPresent(locked -> {
     *         // re-check eligibility on fresh data
     *         // create DeliveryAssignment
     *         // update currentAssignedWeight
     *     });
     * </pre>
     *
     * Only the SELECTED partner is locked — never the entire partner table.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ud FROM UserDetails ud WHERE ud.id = :id")
    Optional<UserDetails> findByIdWithPessimisticLock(@Param("id") Long id);
}
