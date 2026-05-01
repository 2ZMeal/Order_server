package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 주문 취소 이벤트
 * → payment-service : 결제 취소 요청 (requiresPaymentCancellation=true)
 * → shipment-service: 배달 취소 요청 (requiresShipmentCancellation=true, DELIVERING 이전)
 * → notification-service: OrderStatusChangedEvent로 별도 발행
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {

    private UUID orderId;
    private UUID companyId;
    private String userUsername;
    private String cancelledBy;

    /**
     * true  → payment-service가 결제 취소 처리
     * false → 결제 전(READY) 취소이므로 결제 취소 불필요
     */
    private boolean requiresPaymentCancellation;

    /**
     * true  → shipment-service가 배달 취소 처리 (CONFIRMED 상태에서 취소)
     * false → 아직 배달 요청이 안 간 상태 (READY/PENDING) 또는 이미 DELIVERING
     */
    private boolean requiresShipmentCancellation;

    private LocalDateTime occurredAt;
}
