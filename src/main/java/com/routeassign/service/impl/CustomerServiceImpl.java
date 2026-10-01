package com.routeassign.service.impl;

import com.routeassign.domain.entity.CustomerDetails;
import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.CustomerDetailsResponse;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.CustomerDetailsRepository;
import com.routeassign.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private final CustomerDetailsRepository customerDetailsRepository;

    @Override
    public CustomerDetailsResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public CustomerDetailsResponse getByAuthId(Long authId) {
        CustomerDetails customer = customerDetailsRepository.findByAuth_UserId(authId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerDetails", "authId", authId));
        return toResponse(customer);
    }

    @Override
    public List<CustomerDetailsResponse> getAll() {
        return customerDetailsRepository.findAll()
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CustomerDetailsResponse updateLocation(Long id, UpdateLocationRequest request) {
        CustomerDetails customer = findById(id);
        customer.setLatitude(request.getLatitude());
        customer.setLongitude(request.getLongitude());
        return toResponse(customerDetailsRepository.save(customer));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        CustomerDetails customer = findById(id);
        customer.getAuth().setIsActive(false);
        customerDetailsRepository.save(customer);
        log.info("Deactivated customer id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CustomerDetails findById(Long id) {
        return customerDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerDetails", "id", id));
    }

    CustomerDetailsResponse toResponse(CustomerDetails c) {
        return CustomerDetailsResponse.builder()
                .id(c.getId())
                .authId(c.getAuth().getUserId())
                .username(c.getAuth().getUsername())
                .email(c.getAuth().getEmail())
                .mobileNo(c.getMobileNo())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .build();
    }
}
