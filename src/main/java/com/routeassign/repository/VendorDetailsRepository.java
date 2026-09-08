package com.routeassign.repository;

import com.routeassign.domain.entity.UserAuth;
import com.routeassign.domain.entity.VendorDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorDetailsRepository extends JpaRepository<VendorDetails, Long> {

    Optional<VendorDetails> findByAuth(UserAuth auth);

    Optional<VendorDetails> findByAuth_UserId(Long authId);

    List<VendorDetails> findAllByIsActive(Boolean isActive);
}
