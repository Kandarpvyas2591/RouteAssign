package com.routeassign.repository;

import com.routeassign.domain.entity.UserAuth;
import com.routeassign.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAuthRepository extends JpaRepository<UserAuth, Long> {

    Optional<UserAuth> findByEmail(String email);

    Optional<UserAuth> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<UserAuth> findAllByRole(UserRole role);

    List<UserAuth> findAllByIsActive(Boolean isActive);
}
