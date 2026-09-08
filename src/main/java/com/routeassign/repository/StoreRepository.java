package com.routeassign.repository;

import com.routeassign.domain.entity.Item;
import com.routeassign.domain.entity.Store;
import com.routeassign.domain.entity.VendorDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findAllByVendor(VendorDetails vendor);

    List<Store> findAllByVendor_Id(Long vendorId);

    Optional<Store> findByVendorAndItem(VendorDetails vendor, Item item);

    Optional<Store> findByVendor_IdAndItem_Id(Long vendorId, Long itemId);

    boolean existsByVendorAndItem(VendorDetails vendor, Item item);
}
