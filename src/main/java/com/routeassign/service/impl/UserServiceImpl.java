package com.routeassign.service.impl;

import com.routeassign.domain.entity.UserDetails;
import com.routeassign.dto.request.UpdateAvailabilityRequest;
import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.UserDetailsResponse;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.UserDetailsRepository;
import com.routeassign.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserDetailsRepository userDetailsRepository;

    @Override
    public UserDetailsResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public UserDetailsResponse getByAuthId(Long authId) {
        UserDetails partner = userDetailsRepository.findByAuth_UserId(authId)
                .orElseThrow(() -> new ResourceNotFoundException("UserDetails", "authId", authId));
        return toResponse(partner);
    }

    @Override
    public List<UserDetailsResponse> getAllActiveDeliveryPartners() {
        return userDetailsRepository.findAllActiveDeliveryPartners()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public UserDetailsResponse updateLocation(Long id, UpdateLocationRequest request) {
        UserDetails partner = findById(id);
        partner.setLatitude(request.getLatitude());
        partner.setLongitude(request.getLongitude());
        return toResponse(userDetailsRepository.save(partner));
    }

    @Override
    @Transactional
    public UserDetailsResponse updateAvailability(Long id, UpdateAvailabilityRequest request) {
        UserDetails partner = findById(id);
        partner.setIsAvailable(request.getIsAvailable());
        return toResponse(userDetailsRepository.save(partner));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        UserDetails partner = findById(id);
        partner.setIsActive(false);
        partner.setIsAvailable(false);
        userDetailsRepository.save(partner);
        log.info("Deactivated delivery partner id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserDetails findById(Long id) {
        return userDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UserDetails", "id", id));
    }

    private UserDetailsResponse toResponse(UserDetails u) {
        return UserDetailsResponse.builder()
                .id(u.getId())
                .authId(u.getAuth().getUserId())
                .username(u.getAuth().getUsername())
                .email(u.getAuth().getEmail())
                .role(u.getAuth().getRole())
                .mobileNo(u.getMobileNo())
                .latitude(u.getLatitude())
                .longitude(u.getLongitude())
                .isActive(u.getIsActive())
                .isAvailable(u.getIsAvailable())
                .capacity(u.getCapacity())
                .currentAssignedWeight(u.getCurrentAssignedWeight())
                .rating(u.getRating())
                .build();
    }
}
