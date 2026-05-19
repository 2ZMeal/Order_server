package com.ezmeal.order.infrastructure.kafka.dto;

import com.ezmeal.common.message.DomainEvent;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentFailedMessage implements DomainEvent {
    private UUID paymentId;
    private UUID orderId;
    private UUID userId;
    private String reason;
}
