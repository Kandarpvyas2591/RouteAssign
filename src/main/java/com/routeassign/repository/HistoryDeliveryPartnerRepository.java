package com.routeassign.repository;

import com.routeassign.domain.entity.HistoryDeliveryPartner;
import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.enums.HistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistoryDeliveryPartnerRepository extends JpaRepository<HistoryDeliveryPartner, Long> {

    List<HistoryDeliveryPartner> findAllByDeliveryPartner(UserDetails partner);

    List<HistoryDeliveryPartner> findAllByDeliveryPartner_Id(Long partnerId);

    List<HistoryDeliveryPartner> findAllByDeliveryPartner_IdAndStatus(Long partnerId, HistoryStatus status);

    /**
     * Checks whether a delivery partner has ever completed a delivery.
     * Used by the "never-assigned" priority rule in the assignment algorithm.
     */
    boolean existsByDeliveryPartner_Id(Long partnerId);

    /**
     * Returns the most recent completed history record for a partner.
     * Used to calculate idle time (current time - completedAt).
     */
    @Query("""
            SELECT h FROM HistoryDeliveryPartner h
            WHERE h.deliveryPartner.id = :partnerId
              AND h.status = 'COMPLETED'
            ORDER BY h.completedAt DESC
            LIMIT 1
            """)
    Optional<HistoryDeliveryPartner> findLatestCompletedByPartner(@Param("partnerId") Long partnerId);

    /** Count of completed deliveries for a partner — used in leaderboard and dashboard. */
    long countByDeliveryPartner_IdAndStatus(Long partnerId, HistoryStatus status);

    /** Total deliveries (all statuses) for a partner — used in the partner dashboard. */
    long countByDeliveryPartner_Id(Long partnerId);
}
