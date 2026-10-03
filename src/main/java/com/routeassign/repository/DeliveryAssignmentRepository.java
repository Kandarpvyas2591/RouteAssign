package com.routeassign.repository;

import com.routeassign.domain.entity.DeliveryAssignment;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.entity.VendorDetails;
import com.routeassign.domain.enums.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, Long> {

    Optional<DeliveryAssignment> findByOrder_OrderId(Long orderId);

    List<DeliveryAssignment> findAllByDeliveryPartner(UserDetails deliveryPartner);

    List<DeliveryAssignment> findAllByDeliveryPartner_Id(Long partnerId);

    List<DeliveryAssignment> findAllByVendor(VendorDetails vendor);

    List<DeliveryAssignment> findAllByVendor_Id(Long vendorId);

    List<DeliveryAssignment> findAllByDeliveryStatus(DeliveryStatus status);

    /**
     * Used by the same-vendor reuse check.
     * Finds active (non-terminal) assignments for a partner at a specific vendor.
     */
    @Query("""
            SELECT da FROM DeliveryAssignment da
            WHERE da.deliveryPartner.id = :partnerId
              AND da.vendor.id = :vendorId
              AND da.deliveryStatus NOT IN ('DELIVERED', 'CANCELLED', 'FAILED')
            """)
    List<DeliveryAssignment> findActiveAssignmentsByPartnerAndVendor(
            @Param("partnerId") Long partnerId,
            @Param("vendorId") Long vendorId);

    /**
     * Finds all non-terminal assignments for a partner.
     * Used to calculate current assigned weight.
     */
    @Query("""
            SELECT da FROM DeliveryAssignment da
            WHERE da.deliveryPartner.id = :partnerId
              AND da.deliveryStatus NOT IN ('DELIVERED', 'CANCELLED', 'FAILED')
            """)
    List<DeliveryAssignment> findActiveAssignmentsByPartner(@Param("partnerId") Long partnerId);

    /**
     * Returns the single active assignment with the latest expectedDeliveryTime
     * for a given partner. Used to determine when a busy partner will be free
     * so the algorithm can compute a rest-adjusted ETA for them.
     *
     * Returns empty if the partner has no active assignments (i.e. they are free).
     */
    @Query("""
            SELECT da FROM DeliveryAssignment da
            WHERE da.deliveryPartner.id = :partnerId
              AND da.deliveryStatus NOT IN ('DELIVERED', 'CANCELLED', 'FAILED')
            ORDER BY da.expectedDeliveryTime DESC
            LIMIT 1
            """)
    Optional<DeliveryAssignment> findLatestActiveAssignmentByPartner(@Param("partnerId") Long partnerId);

    /**
     * Idempotency check — returns any active (non-terminal) assignment for the
     * given order, regardless of delivery partner.
     *
     * Called at the very start of the assign flow to detect duplicate requests:
     * <pre>
     * if (findActiveAssignmentByOrderId(orderId).isPresent()) {
     *     return existingAssignment;   // return early, do not double-assign
     * }
     * </pre>
     *
     * Only non-terminal statuses are matched; a cancelled or failed assignment
     * does not prevent a fresh assignment attempt.
     */
    @Query("""
            SELECT da FROM DeliveryAssignment da
            WHERE da.order.orderId = :orderId
              AND da.deliveryStatus NOT IN ('DELIVERED', 'CANCELLED', 'FAILED',
                                           'REJECTED', 'EXPIRED', 'DELIVERY_FAILED',
                                           'WAITING_FOR_PARTNER')
            """)
    Optional<DeliveryAssignment> findActiveAssignmentByOrderId(@Param("orderId") Long orderId);

    /**
     * Lightweight existence check — equivalent to {@link #findActiveAssignmentByOrderId}
     * but cheaper when only a boolean answer is needed.
     */
    @Query("""
            SELECT COUNT(da) > 0 FROM DeliveryAssignment da
            WHERE da.order.orderId = :orderId
              AND da.deliveryStatus NOT IN ('DELIVERED', 'CANCELLED', 'FAILED',
                                           'REJECTED', 'EXPIRED', 'DELIVERY_FAILED',
                                           'WAITING_FOR_PARTNER')
            """)
    boolean existsActiveAssignmentForOrder(@Param("orderId") Long orderId);

    /**
     * Returns ALL assignment attempts for an order (all statuses, all partners).
     * Used to reconstruct the full reassignment history for a given order.
     */
    @Query("""
            SELECT da FROM DeliveryAssignment da
            WHERE da.order.orderId = :orderId
            ORDER BY da.attemptNumber ASC
            """)
    List<DeliveryAssignment> findAllByOrderId(@Param("orderId") Long orderId);

    /**
     * Returns the total number of assignment attempts made for an order.
     * Used by {@code AssignmentReassignmentService} to enforce MAX_ASSIGNMENT_ATTEMPTS.
     */
    @Query("""
            SELECT COUNT(da) FROM DeliveryAssignment da
            WHERE da.order.orderId = :orderId
            """)
    long countByOrderId(@Param("orderId") Long orderId);

    /**
     * Returns the assignment attempt with the specified attempt number for an order.
     */
    @Query("""
            SELECT da FROM DeliveryAssignment da
            WHERE da.order.orderId = :orderId
              AND da.attemptNumber = :attemptNumber
            """)
    Optional<DeliveryAssignment> findByOrderIdAndAttemptNumber(
            @Param("orderId")       Long orderId,
            @Param("attemptNumber") int  attemptNumber);

    /**
     * Finds all ASSIGNED assignments whose acceptance window has expired.
     *
     * An assignment is expired when:
     *   status = ASSIGNED
     *   AND assigned_at + timeoutMinutes <= now
     *
     * Used by {@code AssignmentExpiryService} to detect and trigger expiry.
     * The {@code timeoutThreshold} parameter should be passed as
     * {@code LocalDateTime.now().minusMinutes(timeoutMinutes)}.
     */
    @Query("""
            SELECT da FROM DeliveryAssignment da
            WHERE da.deliveryStatus = 'ASSIGNED'
              AND da.assignedAt <= :timeoutThreshold
            """)
    List<DeliveryAssignment> findExpiredAssignments(
            @Param("timeoutThreshold") java.time.LocalDateTime timeoutThreshold);
}
