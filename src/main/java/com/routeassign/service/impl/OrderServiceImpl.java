package com.routeassign.service.impl;

import com.routeassign.domain.entity.*;
import com.routeassign.domain.enums.OrderStatus;
import com.routeassign.dto.request.OrderItemRequest;
import com.routeassign.dto.request.OrderRequest;
import com.routeassign.dto.response.*;
import com.routeassign.exception.*;
import com.routeassign.repository.*;
import com.routeassign.service.DeliveryAssignmentService;
import com.routeassign.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository           orderRepository;
    private final CustomerDetailsRepository customerDetailsRepository;
    private final VendorDetailsRepository   vendorDetailsRepository;
    private final ItemRepository            itemRepository;
    private final StoreRepository           storeRepository;
    private final DeliveryAssignmentService deliveryAssignmentService;

    // ── Place order ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DeliveryAssignmentResponse placeOrder(Long customerId, OrderRequest request) {

        // 1. Validate customer
        CustomerDetails customer = customerDetailsRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerDetails", "id", customerId));

        // 2. Validate vendor
        VendorDetails vendor = vendorDetailsRepository.findById(request.getVendorId())
                .orElseThrow(() -> new ResourceNotFoundException("VendorDetails", "id", request.getVendorId()));

        if (!vendor.getIsActive()) {
            throw new BadRequestException("Vendor id=" + vendor.getId() + " is not active.");
        }

        // 3. Build order items — validate stock and snapshot weights
        List<OrderItem> orderItems = new ArrayList<>();
        double totalWeight = 0.0;

        for (OrderItemRequest itemReq : request.getItems()) {
            Item item = itemRepository.findById(itemReq.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Item", "id", itemReq.getItemId()));

            Store store = storeRepository.findByVendor_IdAndItem_Id(vendor.getId(), item.getId())
                    .orElseThrow(() -> new BadRequestException(
                            "Item '" + item.getName() + "' is not available at vendor id=" + vendor.getId()));

            if (store.getQuantity() < itemReq.getQuantity()) {
                throw new InsufficientStockException(item.getName(),
                        itemReq.getQuantity(), store.getQuantity());
            }

            OrderItem orderItem = OrderItem.builder()
                    .item(item)
                    .quantity(itemReq.getQuantity())
                    .weight(item.getWeight())        // snapshot weight at order time
                    .build();
            orderItems.add(orderItem);
            totalWeight += item.getWeight() * itemReq.getQuantity();
        }

        // 4. Persist Order
        Order order = Order.builder()
                .customer(customer)
                .vendor(vendor)
                .orderStatus(OrderStatus.PENDING)
                .deliveryLocationLatitude(request.getDeliveryLocationLatitude())
                .deliveryLocationLongitude(request.getDeliveryLocationLongitude())
                .totalWeight(totalWeight)
                .orderItems(new ArrayList<>())
                .build();

        order = orderRepository.save(order);

        // Link order items to the saved order
        for (OrderItem oi : orderItems) {
            oi.setOrder(order);
            order.getOrderItems().add(oi);
        }
        order = orderRepository.save(order);

        // 5. Deduct stock
        for (OrderItemRequest itemReq : request.getItems()) {
            Store store = storeRepository.findByVendor_IdAndItem_Id(
                    vendor.getId(), itemReq.getItemId()).orElseThrow();
            store.setQuantity(store.getQuantity() - itemReq.getQuantity());
            storeRepository.save(store);
        }

        log.info("Order id={} placed for customerId={} vendorId={} weight={}kg",
                order.getOrderId(), customerId, vendor.getId(), totalWeight);

        // 6. Trigger auto-assignment — throws NoEligiblePartnerException if none found
        DeliveryAssignmentResponse assignment = deliveryAssignmentService.assign(order);

        // 7. Mark order as ASSIGNED
        order.setOrderStatus(OrderStatus.ASSIGNED);
        orderRepository.save(order);

        return assignment;
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    public OrderResponse getById(Long orderId) {
        return toResponse(findById(orderId));
    }

    @Override
    public List<OrderResponse> getByCustomerId(Long customerId) {
        return orderRepository.findAllByCustomer_Id(customerId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<OrderResponse> getByVendorId(Long vendorId) {
        return orderRepository.findAllByVendor_Id(vendorId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public List<OrderResponse> getByStatus(OrderStatus status) {
        return orderRepository.findAllByOrderStatus(status)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long orderId, OrderStatus newStatus) {
        Order order = findById(orderId);
        validateStatusTransition(order.getOrderStatus(), newStatus);
        order.setOrderStatus(newStatus);
        return toResponse(orderRepository.save(order));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Order findById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
    }

    /**
     * Enforces a basic forward-only status flow.
     * Terminal statuses (DELIVERED, CANCELLED) cannot transition further.
     */
    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.DELIVERED || current == OrderStatus.CANCELLED) {
            throw new InvalidStatusTransitionException("Order", current, next);
        }
        if (next == OrderStatus.PENDING) {
            throw new InvalidStatusTransitionException("Order", current, next);
        }
    }

    private OrderResponse toResponse(Order o) {
        List<OrderItemResponse> itemResponses = o.getOrderItems().stream()
                .map(oi -> OrderItemResponse.builder()
                        .id(oi.getId())
                        .itemId(oi.getItem().getId())
                        .itemName(oi.getItem().getName())
                        .quantity(oi.getQuantity())
                        .weight(oi.getWeight())
                        .lineWeight(oi.getWeight() * oi.getQuantity())
                        .build())
                .toList();

        return OrderResponse.builder()
                .orderId(o.getOrderId())
                .customerId(o.getCustomer().getId())
                .customerName(o.getCustomer().getAuth().getUsername())
                .vendorId(o.getVendor().getId())
                .vendorName(o.getVendor().getAuth().getUsername())
                .orderStatus(o.getOrderStatus())
                .createdAt(o.getCreatedAt())
                .deliveryLocationLatitude(o.getDeliveryLocationLatitude())
                .deliveryLocationLongitude(o.getDeliveryLocationLongitude())
                .totalWeight(o.getTotalWeight())
                .items(itemResponses)
                .build();
    }
}
