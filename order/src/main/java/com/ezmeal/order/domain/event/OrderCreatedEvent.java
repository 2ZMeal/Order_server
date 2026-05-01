package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * SAGA Step 1: 주문 생성 완료
 * → payment-service: 결제 요청
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private UUID orderId;
    private UUID companyId;
    private String userUsername;
    private Integer totalPrice;
    private String deliveryAddress;
    private List<OrderItemPayload> items;
    private LocalDateTime occurredAt;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemPayload {
        private String productName;
        private Integer productPrice;
        private Integer quantity;
    }
}
