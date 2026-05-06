package com.ezmeal.order.domain.event;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 주문 완료 이벤트 (status = COMPLETED)
 * → notification-service: 리뷰 작성 요청 알림 발송
 *
 * 발행 시점: DELIVERING → COMPLETED 상태 변경 시
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCompletedEvent {

    private UUID orderId;
    private UUID companyId;
    private String companyName;
    private String userId;
    private List<String> productNames;   // 리뷰 대상 상품 목록
    private Integer totalPrice;
    private LocalDateTime completedAt;
}
