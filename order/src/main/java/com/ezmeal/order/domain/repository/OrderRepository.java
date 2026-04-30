package com.ezmeal.order.domain.repository;

import com.delivery.orderservice.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    Page<Order> findAll(Pageable pageable);
    Page<Order> findByCustomerUsername(String username, Pageable pageable);
    Page<Order> findByStoreId(UUID storeId, Pageable pageable);
    Page<Order> searchWithFilters(
            UUID storeId,
            String customerUsername,
            Order.OrderStatus status,
            String productName,
            Integer minAmount,
            Integer maxAmount,
            Pageable pageable
    );
}
