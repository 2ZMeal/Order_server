package com.ezmeal.order.domain.event;

import com.ezmeal.order.domain.entity.OrderItem;
import java.util.List;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.ezmeal.common.message.DomainEvent;

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
public class OrderCancelledEvent implements DomainEvent {

    private UUID orderId;
    private List<OrderItem> orderItems;
    private String userId;
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

    /**
     * true  → 재고 예약이 완료된 상태에서 취소
     *         product-service 가 재고 복구 처리 후 stock.restore.result 발행
     * false → 재고 예약 전(READY) 취소이므로 복구 불필요
     */
    private boolean requiresStockRestore;           // 추가

    /**
     * 복구 대상 상품 목록
     * product-service 가 어떤 상품의 재고를 복구할지 알 수 있도록 전달
     */
    private List<StockRestoreItem> stockRestoreItems;  // 추가

    private LocalDateTime occurredAt;

    @Getter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StockRestoreItem {
        private UUID productId;
        private Integer quantity;
    }
}
