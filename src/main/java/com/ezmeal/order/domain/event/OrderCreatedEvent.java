package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.ezmeal.common.message.DomainEvent;

/**
 * SAGA Step 1: 주문 생성 완료
 * → payment-service: 결제 요청
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent implements DomainEvent {

    private UUID orderId;
    private UUID companyId;
    private String userId;
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
