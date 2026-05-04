package com.ezmeal.order.infrastructure.kafka.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * payment-service → order-service 수신 메시지
 * Topic: payment.result
 */
@Getter
@NoArgsConstructor
public class PaymentResultMessage {
    private UUID orderId;
    private UUID paymentId;
    private String userId;
    private boolean success;
    private String reason;     // 실패 시 사유
}
