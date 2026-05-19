package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.ezmeal.common.message.DomainEvent;
/**
 * 주문 상태 변경 이벤트 (모든 상태 변경 시 발행)
 * → notification-service: 고객에게 주문 상태 변경 알림 발송
 *
 * 발행 시점:
 * - READY → PENDING    (결제 진행 중)
 * - PENDING → CONFIRMED (결제 완료)
 * - CONFIRMED → DELIVERING (배달 시작)
 * - DELIVERING → COMPLETED (배달 완료)
 * - ANY → CANCELLED    (주문 취소)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusChangedEvent implements DomainEvent {

    private UUID orderId;
    private String userId;
    private UUID companyId;
    private String previousStatus;
    private String currentStatus;
    private String changedBy;
    private LocalDateTime occurredAt;
}
