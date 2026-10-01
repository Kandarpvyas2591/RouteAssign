package com.routeassign.service.impl;

import com.routeassign.domain.enums.DeliveryStatus;
import com.routeassign.domain.enums.OrderStatus;
import com.routeassign.dto.response.DashboardStatsResponse;
import com.routeassign.repository.*;
import com.routeassign.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final CustomerDetailsRepository    customerDetailsRepository;
    private final VendorDetailsRepository      vendorDetailsRepository;
    private final UserDetailsRepository        userDetailsRepository;
    private final OrderRepository              orderRepository;
    private final DeliveryAssignmentRepository deliveryAssignmentRepository;

    @Override
    public DashboardStatsResponse getStats() {

        // ── User counts ───────────────────────────────────────────────────────
        long totalCustomers          = customerDetailsRepository.count();
        long totalVendors            = vendorDetailsRepository.count();
        long activeVendors           = vendorDetailsRepository.findAllByIsActive(true).size();

        List<com.routeassign.domain.entity.UserDetails> allActivePartners =
                userDetailsRepository.findAllActiveDeliveryPartners();
        long totalDeliveryPartners   = allActivePartners.size();
        long availablePartners       = allActivePartners.stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsAvailable()))
                .count();

        // ── Order counts ──────────────────────────────────────────────────────
        long totalOrders     = orderRepository.count();
        long pendingOrders   = orderRepository.findAllByOrderStatus(OrderStatus.PENDING).size();
        long assignedOrders  = orderRepository.findAllByOrderStatus(OrderStatus.ASSIGNED).size();
        long pickedUpOrders  = orderRepository.findAllByOrderStatus(OrderStatus.PICKED_UP).size();
        long inTransitOrders = orderRepository.findAllByOrderStatus(OrderStatus.IN_TRANSIT).size();
        long deliveredOrders = orderRepository.findAllByOrderStatus(OrderStatus.DELIVERED).size();
        long cancelledOrders = orderRepository.findAllByOrderStatus(OrderStatus.CANCELLED).size();

        // ── Live assignment counts ────────────────────────────────────────────
        long assignedAssignments         = deliveryAssignmentRepository
                .findAllByDeliveryStatus(DeliveryStatus.ASSIGNED).size();
        long enRouteToVendorAssignments  = deliveryAssignmentRepository
                .findAllByDeliveryStatus(DeliveryStatus.EN_ROUTE_TO_VENDOR).size();
        long collectedAssignments        = deliveryAssignmentRepository
                .findAllByDeliveryStatus(DeliveryStatus.COLLECTED).size();
        long enRouteToCustomerAssignments = deliveryAssignmentRepository
                .findAllByDeliveryStatus(DeliveryStatus.EN_ROUTE_TO_CUSTOMER).size();

        long liveAssignments = assignedAssignments + enRouteToVendorAssignments
                + collectedAssignments + enRouteToCustomerAssignments;

        // ── Capacity snapshot ─────────────────────────────────────────────────
        double totalCapacity = allActivePartners.stream()
                .mapToDouble(p -> p.getCapacity() != null ? p.getCapacity() : 0.0)
                .sum();
        double usedCapacity = allActivePartners.stream()
                .mapToDouble(p -> p.getCurrentAssignedWeight() != null
                        ? p.getCurrentAssignedWeight() : 0.0)
                .sum();

        return DashboardStatsResponse.builder()
                .totalCustomers(totalCustomers)
                .totalVendors(totalVendors)
                .activeVendors(activeVendors)
                .totalDeliveryPartners(totalDeliveryPartners)
                .activeDeliveryPartners(totalDeliveryPartners)
                .availableDeliveryPartners(availablePartners)
                .totalOrders(totalOrders)
                .pendingOrders(pendingOrders)
                .assignedOrders(assignedOrders)
                .inProgressOrders(pickedUpOrders + inTransitOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .liveAssignments(liveAssignments)
                .assignedAssignments(assignedAssignments)
                .enRouteToVendorAssignments(enRouteToVendorAssignments)
                .collectedAssignments(collectedAssignments)
                .enRouteToCustomerAssignments(enRouteToCustomerAssignments)
                .totalSystemCapacityKg(totalCapacity)
                .usedSystemCapacityKg(usedCapacity)
                .freeSystemCapacityKg(totalCapacity - usedCapacity)
                .build();
    }
}
