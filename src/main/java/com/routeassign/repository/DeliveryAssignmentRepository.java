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
}
