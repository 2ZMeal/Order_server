package com.ezmeal.order.infrastructure.persistence;

import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.repository.OrderRepository;
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
    public Page<Order> findByUserId(String userId, Pageable pageable) {
        return jpaRepository.findByUserId(userId, pageable);
    }

    @Override
    public Page<Order> findByCompanyId(UUID companyId, Pageable pageable) {
        return jpaRepository.findByCompanyId(companyId, pageable);
    }

    @Override
    public Page<Order> searchWithFilters(UUID companyId, String userId,
                                         Order.OrderStatus status, String productName,
                                         Integer minAmount, Integer maxAmount, Pageable pageable) {
        return jpaRepository.searchWithFilters(
                companyId, userId, status, productName, minAmount, maxAmount, pageable);
    }
}
