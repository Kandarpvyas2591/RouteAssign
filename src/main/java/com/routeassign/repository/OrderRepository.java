package com.routeassign.repository;

import com.routeassign.domain.entity.CustomerDetails;
import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.VendorDetails;
import com.routeassign.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByCustomer(CustomerDetails customer);

    List<Order> findAllByCustomer_Id(Long customerId);

    List<Order> findAllByVendor(VendorDetails vendor);

    List<Order> findAllByVendor_Id(Long vendorId);

    List<Order> findAllByOrderStatus(OrderStatus status);

    List<Order> findAllByCustomer_IdAndOrderStatus(Long customerId, OrderStatus status);

    List<Order> findAllByVendor_IdAndOrderStatus(Long vendorId, OrderStatus status);
}
