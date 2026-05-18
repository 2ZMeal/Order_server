package com.ezmeal.order.infrastructure.kafka.dto;

import com.ezmeal.common.message.DomainEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * product-service 의 ProductReservationRestoreFailedEvent 를 수신하는 DTO
 * 토픽: product.quantity.restore.failed
 */
@Getter
@NoArgsConstructor
public class StockRestoreFailedMessage implements DomainEvent {
    private UUID orderId;
    private UUID productId;
    private String reason;
}
