package com.ezmeal.order.infrastructure.persistence;

import com.delivery.orderservice.domain.entity.Order;
import com.delivery.orderservice.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain Repository Interface(DIP)의 JPA 구현체
 * Domain Layer는 이 클래스를 알지 못하고 인터페이스만 의존
 */
@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderJpaRepository jpaRepository;

    @Override
    public Order save(Order order) {
        return jpaRepository.save(order);
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Page<Order> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }

    @Override
    public Page<Order> findByCustomerUsername(String username, Pageable pageable) {
        return jpaRepository.findByCustomerUsername(username, pageable);
    }

    @Override
    public Page<Order> findByStoreId(UUID storeId, Pageable pageable) {
        return jpaRepository.findByStoreId(storeId, pageable);
    }

    @Override
    public Page<Order> searchWithFilters(UUID storeId, String customerUsername,
                                         Order.OrderStatus status, String productName,
                                         Integer minAmount, Integer maxAmount, Pageable pageable) {
        return jpaRepository.searchWithFilters(
                storeId, customerUsername, status, productName, minAmount, maxAmount, pageable);
    }
}
