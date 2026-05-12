package com.ezmeal.order.domain.entity;

import com.ezmeal.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "p_order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class OrderItem extends BaseEntity{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "product_id", nullable = false, length = 255)
    private UUID productId;      // 추가: 재고 복구 시 필요

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_price", nullable = false)
    private Integer productPrice;

    @Column(nullable = false)
    private Integer quantity;


    public static OrderItem create(Order order, UUID companyId, UUID productId, String productName,
                                   Integer productPrice, Integer quantity) {
        return OrderItem.builder()
                .order(order)
                .companyId(companyId)
                .productId(productId)      // 추가
                .productName(productName)
                .productPrice(productPrice)
                .quantity(quantity)
                .build();
    }
}
