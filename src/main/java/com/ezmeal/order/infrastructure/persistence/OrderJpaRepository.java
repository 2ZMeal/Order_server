package com.ezmeal.order.infrastructure.persistence;

import com.ezmeal.order.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByUserId(String userId, Pageable pageable);


    // OrderItem 의 companyId 로 조회, DISTINCT 로 Order 중복 제거
    @Query("""
    SELECT DISTINCT o FROM Order o
    JOIN o.orderItems oi
    WHERE oi.companyId = :companyId
      AND o.deletedAt IS NULL
      AND oi.deletedAt IS NULL
""")
    Page<Order> findByCompanyId(@Param("companyId") UUID companyId, Pageable pageable);

    /**
     * 동적 조건 검색 (관리자/사장님/고객 공통)
     * - companyId, userId, status, productName, 금액 범위 모두 optional
     */
    @Query("""
        SELECT DISTINCT o FROM Order o
        JOIN o.orderItems oi
         WHERE o.deletedAt IS NULL
          AND oi.deletedAt IS NULL
          AND (:companyId IS NULL OR  oi.companyId = :companyId)
          AND (:userId IS NULL OR o.userId = :userId)
          AND (:status IS NULL OR o.status = :status)
          AND (:productName IS NULL OR oi.productName LIKE %:productName%)
          AND (:minAmount IS NULL OR o.totalPrice >= :minAmount)
          AND (:maxAmount IS NULL OR o.totalPrice <= :maxAmount)
    """)
    Page<Order> searchWithFilters(
            @Param("companyId") UUID companyId,
            @Param("userId") String userId,
            @Param("status") Order.OrderStatus status,
            @Param("productName") String productName,
            @Param("minAmount") Integer minAmount,
            @Param("maxAmount") Integer maxAmount,
            Pageable pageable
    );
}
