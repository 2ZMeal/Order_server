package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import com.ezmeal.common.message.DomainEvent;
/**
 * 배달 요청 이벤트
 * → shipment-service: 배달 기사 배정 및 배달 시작
 *
 * 발행 시점: 결제 완료(CONFIRMED) 직후
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentRequestedEvent implements DomainEvent {

    private UUID orderId;
    private UUID companyId;
    private String userId;
    private String deliveryAddress;
    private String requestNote;
    private LocalDateTime occurredAt;
}

