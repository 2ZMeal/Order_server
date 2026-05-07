package com.ezmeal.order.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "product_price", nullable = false)
    private Integer productPrice;

    @Column(nullable = false)
    private Integer quantity;


    public static OrderItem create(Order order, UUID companyId, String productName,
                                   Integer productPrice, Integer quantity) {
        return OrderItem.builder()
                .order(order)
                .companyId(companyId)
                .productName(productName)
                .productPrice(productPrice)
                .quantity(quantity)
                .build();
    }
}
