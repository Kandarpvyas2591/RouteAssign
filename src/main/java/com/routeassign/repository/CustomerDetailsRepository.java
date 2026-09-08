package com.routeassign.repository;

import com.routeassign.domain.entity.CustomerDetails;
import com.routeassign.domain.entity.UserAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerDetailsRepository extends JpaRepository<CustomerDetails, Long> {

    Optional<CustomerDetails> findByAuth(UserAuth auth);

    Optional<CustomerDetails> findByAuth_UserId(Long authId);
}
