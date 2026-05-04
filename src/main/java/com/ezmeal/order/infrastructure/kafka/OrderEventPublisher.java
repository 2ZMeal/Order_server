package com.ezmeal.order.infrastructure.kafka;

import com.ezmeal.order.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * [STEP 1] 주문 생성 → payment-service로 결제 요청
     * Topic: order.created
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        send(KafkaTopics.ORDER_CREATED, event.getOrderId().toString(), event);
    }

    /**
     * [STEP 2] 결제 완료 → shipment-service로 배달 요청
     * Topic: shipment.requested
     */
    public void publishShipmentRequested(ShipmentRequestedEvent event) {
        send(KafkaTopics.SHIPMENT_REQUESTED, event.getOrderId().toString(), event);
    }

    /**
     * [CANCEL] 주문 취소 → payment-service(결제 취소) + shipment-service(배달 취소)
     * Topic: order.cancelled
     * 각 서비스는 requiresPaymentCancellation / requiresShipmentCancellation 플래그로 처리 여부 판단
     */
    public void publishOrderCancelled(OrderCancelledEvent event) {
        send(KafkaTopics.ORDER_CANCELLED, event.getOrderId().toString(), event);
    }

    /**
     * [STATUS] 모든 주문 상태 변경 시 → notification-service로 알림
     * Topic: order.status.changed
     * 발행 시점: READY→PENDING, PENDING→CONFIRMED, CONFIRMED→DELIVERING,
     *            DELIVERING→COMPLETED, ANY→CANCELLED
     */
    public void publishOrderStatusChanged(OrderStatusChangedEvent event) {
        send(KafkaTopics.ORDER_STATUS_CHANGED, event.getOrderId().toString(), event);
    }

    /**
     * [COMPLETED] 배달 완료 시 → notification-service로 리뷰 요청 알림
     * Topic: order.completed
     * 발행 시점: 상태가 DELIVERING → COMPLETED 로 변경될 때
     */
    public void publishOrderCompleted(OrderCompletedEvent event) {
        send(KafkaTopics.ORDER_COMPLETED, event.getOrderId().toString(), event);
    }

    // ── 공통 발송 ──────────────────────────────────────────────────
    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka] 발행 실패 topic={} key={} error={}",
                                topic, key, ex.getMessage());
                    } else {
                        log.info("[Kafka] 발행 성공 topic={} key={} offset={}",
                                topic, key, result.getRecordMetadata().offset());
                    }
                });
    }
}
