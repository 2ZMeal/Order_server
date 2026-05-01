package com.ezmeal.order.infrastructure.persistence;

import com.delivery.orderservice.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByCustomerUsername(String username, Pageable pageable);

    Page<Order> findByStoreId(UUID storeId, Pageable pageable);

    /**
     * 동적 조건 검색 (관리자/사장님/고객 공통)
     * - storeId, customerUsername, status, productName, 금액 범위 모두 optional
     */
    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN o.orderItems oi
        WHERE (:storeId IS NULL OR o.storeId = :storeId)
          AND (:customerUsername IS NULL OR o.customerUsername = :customerUsername)
          AND (:status IS NULL OR o.status = :status)
          AND (:productName IS NULL OR oi.productName LIKE %:productName%)
          AND (:minAmount IS NULL OR o.totalPrice >= :minAmount)
          AND (:maxAmount IS NULL OR o.totalPrice <= :maxAmount)
    """)
    Page<Order> searchWithFilters(
            @Param("storeId") UUID storeId,
            @Param("customerUsername") String customerUsername,
            @Param("status") Order.OrderStatus status,
            @Param("productName") String productName,
            @Param("minAmount") Integer minAmount,
            @Param("maxAmount") Integer maxAmount,
            Pageable pageable
    );
}
