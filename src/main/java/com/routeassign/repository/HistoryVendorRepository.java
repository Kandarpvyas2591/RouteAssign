package com.routeassign.repository;

import com.routeassign.domain.entity.HistoryVendor;
import com.routeassign.domain.entity.VendorDetails;
import com.routeassign.domain.enums.HistoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistoryVendorRepository extends JpaRepository<HistoryVendor, Long> {

    List<HistoryVendor> findAllByVendor(VendorDetails vendor);

    List<HistoryVendor> findAllByVendor_Id(Long vendorId);

    List<HistoryVendor> findAllByVendor_IdAndStatus(Long vendorId, HistoryStatus status);

    /** Count of records for a vendor with a specific status — used in dashboard stats. */
    long countByVendor_IdAndStatus(Long vendorId, HistoryStatus status);
}
