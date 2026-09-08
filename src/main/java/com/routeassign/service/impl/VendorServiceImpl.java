package com.routeassign.service.impl;

import com.routeassign.domain.entity.VendorDetails;
import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.VendorDetailsResponse;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.VendorDetailsRepository;
import com.routeassign.service.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorServiceImpl implements VendorService {

    private final VendorDetailsRepository vendorDetailsRepository;

    @Override
    public VendorDetailsResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public VendorDetailsResponse getByAuthId(Long authId) {
        VendorDetails vendor = vendorDetailsRepository.findByAuth_UserId(authId)
                .orElseThrow(() -> new ResourceNotFoundException("VendorDetails", "authId", authId));
        return toResponse(vendor);
    }

    @Override
    public List<VendorDetailsResponse> getAllActive() {
        return vendorDetailsRepository.findAllByIsActive(true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public VendorDetailsResponse updateLocation(Long id, UpdateLocationRequest request) {
        VendorDetails vendor = findById(id);
        vendor.setLatitude(request.getLatitude());
        vendor.setLongitude(request.getLongitude());
        return toResponse(vendorDetailsRepository.save(vendor));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        VendorDetails vendor = findById(id);
        vendor.setIsActive(false);
        vendorDetailsRepository.save(vendor);
        log.info("Deactivated vendor id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VendorDetails findById(Long id) {
        return vendorDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VendorDetails", "id", id));
    }

    VendorDetailsResponse toResponse(VendorDetails v) {
        Double ratingValue = (v.getRating() != null) ? v.getRating().getRating() : null;
        return VendorDetailsResponse.builder()
                .id(v.getId())
                .authId(v.getAuth().getUserId())
                .username(v.getAuth().getUsername())
                .email(v.getAuth().getEmail())
                .latitude(v.getLatitude())
                .longitude(v.getLongitude())
                .rating(ratingValue)
                .isActive(v.getIsActive())
                .build();
    }
}
