package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SAGA 보상: 결제 실패
 * → order-service 내부: 주문 CANCELLED 처리
 * → notification-service: 취소 알림 (OrderStatusChangedEvent로 발행)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {

    private UUID orderId;
    private String userName;
    private String reason;
    private LocalDateTime occurredAt;
}
