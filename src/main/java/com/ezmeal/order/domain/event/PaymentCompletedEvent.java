package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SAGA Step 2: 결제 완료 수신 후 내부 발행
 * → shipment-service: 배달 요청
 * → notification-service: 상태 변경 알림 (OrderStatusChangedEvent로 별도 발행)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCompletedEvent {

    private UUID orderId;
    private UUID paymentId;
    private UUID companyId;
    private String userId;
    private String deliveryAddress;
    private Integer totalPrice;
    private LocalDateTime occurredAt;
}
