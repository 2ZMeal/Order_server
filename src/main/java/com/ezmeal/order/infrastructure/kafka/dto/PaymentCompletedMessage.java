package com.ezmeal.order.infrastructure.kafka.dto;

import com.ezmeal.common.message.DomainEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * payment-service 의 PaymentCompletedEvent 를 수신하는 DTO
 * 토픽: payment-completed-event-topic
 */
@Getter
@NoArgsConstructor
public class PaymentCompletedMessage implements DomainEvent {
    private UUID eventId;
    private OffsetDateTime occurredAt;
    private UUID paymentId;
    private UUID orderId;
    private UUID userId;
    private String status;   // "COMPLETED"
    private Integer amount;
    private String paymentKey;
}
