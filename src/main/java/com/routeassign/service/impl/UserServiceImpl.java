package com.routeassign.service.impl;

import com.routeassign.domain.entity.UserDetails;
import com.routeassign.domain.enums.HistoryStatus;
import com.routeassign.dto.request.UpdateAvailabilityRequest;
import com.routeassign.dto.request.UpdateLocationRequest;
import com.routeassign.dto.response.DeliveryAssignmentResponse;
import com.routeassign.dto.response.LeaderboardEntryResponse;
import com.routeassign.dto.response.PartnerDashboardResponse;
import com.routeassign.dto.response.UserDetailsResponse;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.DeliveryAssignmentRepository;
import com.routeassign.repository.HistoryDeliveryPartnerRepository;
import com.routeassign.repository.UserDetailsRepository;
import com.routeassign.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserDetailsRepository            userDetailsRepository;
    private final DeliveryAssignmentRepository     deliveryAssignmentRepository;
    private final HistoryDeliveryPartnerRepository historyDeliveryPartnerRepository;

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

    // ── Partner Dashboard ─────────────────────────────────────────────────────

    @Override
    public PartnerDashboardResponse getPartnerDashboard(Long id) {
        UserDetails partner = findById(id);

        // Active (non-terminal) assignments for this partner
        List<com.routeassign.domain.entity.DeliveryAssignment> activeAssignments =
                deliveryAssignmentRepository.findActiveAssignmentsByPartner(id);

        List<DeliveryAssignmentResponse> activeResponses = activeAssignments.stream()
                .map(da -> DeliveryAssignmentResponse.builder()
                        .id(da.getId())
                        .orderId(da.getOrder().getOrderId())
                        .deliveryPartnerId(da.getDeliveryPartner().getId())
                        .deliveryPartnerName(da.getDeliveryPartner().getAuth().getUsername())
                        .vendorId(da.getVendor().getId())
                        .vendorName(da.getVendor().getAuth().getUsername())
                        .assignedAt(da.getAssignedAt())
                        .expectedDeliveryTime(da.getExpectedDeliveryTime())
                        .deliveryStatus(da.getDeliveryStatus())
                        .distancePartnerToVendor(da.getDistancePartnerToVendor())
                        .distanceVendorToCustomer(da.getDistanceVendorToCustomer())
                        .totalDistance(
                                (da.getDistancePartnerToVendor() != null ? da.getDistancePartnerToVendor() : 0.0)
                                + (da.getDistanceVendorToCustomer() != null ? da.getDistanceVendorToCustomer() : 0.0))
                        .isPartnerBusy(false)
                        .build())
                .toList();

        // Career totals from history
        long totalDeliveries     = historyDeliveryPartnerRepository.countByDeliveryPartner_Id(id);
        long completedDeliveries = historyDeliveryPartnerRepository
                .countByDeliveryPartner_IdAndStatus(id, HistoryStatus.COMPLETED);
        long cancelledDeliveries = historyDeliveryPartnerRepository
                .countByDeliveryPartner_IdAndStatus(id, HistoryStatus.CANCELLED);
        long failedDeliveries    = historyDeliveryPartnerRepository
                .countByDeliveryPartner_IdAndStatus(id, HistoryStatus.FAILED);

        double capacity       = partner.getCapacity() != null ? partner.getCapacity() : 0.0;
        double assignedWeight = partner.getCurrentAssignedWeight() != null
                ? partner.getCurrentAssignedWeight() : 0.0;

        return PartnerDashboardResponse.builder()
                .id(partner.getId())
                .username(partner.getAuth().getUsername())
                .email(partner.getAuth().getEmail())
                .mobileNo(partner.getMobileNo())
                .latitude(partner.getLatitude())
                .longitude(partner.getLongitude())
                .isAvailable(partner.getIsAvailable())
                .isActive(partner.getIsActive())
                .capacityKg(capacity)
                .currentAssignedWeightKg(assignedWeight)
                .remainingCapacityKg(capacity - assignedWeight)
                .rating(partner.getRating())
                .activeAssignments(activeResponses)
                .activeAssignmentCount(activeResponses.size())
                .totalDeliveries(totalDeliveries)
                .completedDeliveries(completedDeliveries)
                .cancelledDeliveries(cancelledDeliveries)
                .failedDeliveries(failedDeliveries)
                .build();
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    @Override
    public List<LeaderboardEntryResponse> getLeaderboard(int limit) {
        List<UserDetails> allActive = userDetailsRepository.findAllActiveDeliveryPartners();

        // Sort: rating desc, then completedDeliveries desc
        Stream<UserDetails> sorted = allActive.stream()
                .sorted(Comparator
                        .comparingDouble((UserDetails p) ->
                                p.getRating() != null ? p.getRating() : 0.0)
                        .reversed()
                        .thenComparingLong((UserDetails p) ->
                                historyDeliveryPartnerRepository
                                        .countByDeliveryPartner_IdAndStatus(p.getId(), HistoryStatus.COMPLETED))
                        .reversed());

        if (limit > 0) {
            sorted = sorted.limit(limit);
        }

        AtomicInteger rank = new AtomicInteger(1);
        return sorted.map(p -> {
            double capacity       = p.getCapacity() != null ? p.getCapacity() : 0.0;
            double assignedWeight = p.getCurrentAssignedWeight() != null
                    ? p.getCurrentAssignedWeight() : 0.0;
            long completed = historyDeliveryPartnerRepository
                    .countByDeliveryPartner_IdAndStatus(p.getId(), HistoryStatus.COMPLETED);
            long cancelled = historyDeliveryPartnerRepository
                    .countByDeliveryPartner_IdAndStatus(p.getId(), HistoryStatus.CANCELLED);

            return LeaderboardEntryResponse.builder()
                    .rank(rank.getAndIncrement())
                    .partnerId(p.getId())
                    .username(p.getAuth().getUsername())
                    .email(p.getAuth().getEmail())
                    .rating(p.getRating())
                    .completedDeliveries(completed)
                    .cancelledDeliveries(cancelled)
                    .currentAssignedWeightKg(assignedWeight)
                    .remainingCapacityKg(capacity - assignedWeight)
                    .isAvailable(p.getIsAvailable())
                    .build();
        }).toList();
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
