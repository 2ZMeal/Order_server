package com.ezmeal.order.domain.repository;

import com.ezmeal.order.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    Page<Order> findAll(Pageable pageable);
    Page<Order> findByUserName(String userName, Pageable pageable);
    Page<Order> findByCompanyId(UUID companyId, Pageable pageable);
    Page<Order> searchWithFilters(
            UUID companyId,
            String userName,
            Order.OrderStatus status,
            String productName,
            Integer minAmount,
            Integer maxAmount,
            Pageable pageable
    );
}
