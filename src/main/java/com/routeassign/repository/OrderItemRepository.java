package com.routeassign.repository;

import com.routeassign.domain.entity.Order;
import com.routeassign.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findAllByOrder(Order order);

    List<OrderItem> findAllByOrder_OrderId(Long orderId);
}
