package com.ezmeal.order.infrastructure.kafka.dto;

import com.ezmeal.common.message.DomainEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * product-service 의 ProductReservationRestoredEvent 를 수신하는 DTO
 * 토픽: product.quantity.restored
 */
@Getter
@NoArgsConstructor
public class StockRestoredMessage implements DomainEvent {
    private UUID orderId;
    private UUID productId;
    private Integer quantity;
}
