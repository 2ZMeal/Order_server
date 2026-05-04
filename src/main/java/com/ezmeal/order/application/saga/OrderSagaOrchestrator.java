package com.ezmeal.order.application.saga;

import com.ezmeal.common.exception.CustomException;
import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.event.*;
import com.ezmeal.order.domain.exception.OrderErrorCode;
import com.ezmeal.order.domain.repository.OrderRepository;
import com.ezmeal.order.infrastructure.kafka.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Choreography-based SAGA Orchestrator
 *
 * ┌─────────────────────────────────────────────────────┐
 * │  SAGA 정상 흐름                                      │
 * │  Order(READY) → [order.created] → payment-service   │
 * │  payment.result(OK) → Order(CONFIRMED)               │
 * │               → [shipment.requested] → shipment-svc  │
 * │               → [order.status.changed] → notif-svc   │
 * │  updateStatus(COMPLETED)                             │
 * │               → [order.status.changed] → notif-svc   │
 * │               → [order.completed] → notif-svc(리뷰)  │
 * │                                                      │
 * │  SAGA 보상 흐름                                      │
 * │  payment.result(FAIL) → Order(CANCELLED)             │
 * │               → [order.status.changed] → notif-svc   │
 * │  cancel() → Order(CANCELLED)                         │
 * │          → [order.cancelled] → payment + shipment    │
 * │          → [order.status.changed] → notif-svc        │
 * └─────────────────────────────────────────────────────┘
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    // ================================================================
    // STEP 1: 주문 생성 → 결제 요청 이벤트 발행
    // ================================================================

    /**
     * 주문 저장 후 payment-service로 결제 요청 이벤트 발행
     * 호출 위치: OrderService.createOrder()
     */
    @Transactional
    public void onOrderCreated(Order order) {
        log.info("[SAGA][STEP1] 주문 생성 완료, 결제 요청 발행 - orderId={}", order.getId());

        // Order 상태를 PENDING(결제 진행 중)으로 전환
        order.markPaymentRequested();
        orderRepository.save(order);

        // payment-service로 결제 요청 이벤트 발행
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .companyId(order.getCompanyId())
                .userName(order.getUserName())
                .totalPrice(order.getTotalPrice())
                .deliveryAddress(order.getDeliveryAddress())
                .items(order.getOrderItems().stream()
                        .map(item -> OrderCreatedEvent.OrderItemPayload.builder()
                                .productName(item.getProductName())
                                .productPrice(item.getProductPrice())
                                .quantity(item.getQuantity())
                                .build())
                        .toList())
                .occurredAt(LocalDateTime.now())
                .build();

        eventPublisher.publishOrderCreated(event);

        // notification-service: 결제 진행 중 알림
        publishStatusChangedEvent(order, Order.OrderStatus.READY, Order.OrderStatus.PENDING);
    }

    // ================================================================
    // STEP 2: 결제 완료 → 배달 요청 + 상태 알림 발행
    // ================================================================

    /**
     * payment-service로부터 결제 완료 수신
     * → Order CONFIRMED 처리
     * → shipment-service로 배달 요청
     * → notification-service로 상태 변경 알림
     * 호출 위치: OrderEventConsumer.consumePaymentResult()
     */
    @Transactional
    public void onPaymentCompleted(UUID orderId, UUID paymentId) {
        log.info("[SAGA][STEP2] 결제 완료, 배달 요청 발행 - orderId={}, paymentId={}", orderId, paymentId);

        Order order = findOrder(orderId);
        Order.OrderStatus prevStatus = order.getStatus();

        // Order 상태 CONFIRMED 처리
        order.markPaymentCompleted();
        orderRepository.save(order);

        // shipment-service로 배달 요청 이벤트 발행
        ShipmentRequestedEvent shipmentEvent = ShipmentRequestedEvent.builder()
                .orderId(order.getId())
                .companyId(order.getCompanyId())
                .userName(order.getUserName())
                .deliveryAddress(order.getDeliveryAddress())
                .requestNote(order.getRequestNote())
                .occurredAt(LocalDateTime.now())
                .build();
        eventPublisher.publishShipmentRequested(shipmentEvent);

        // notification-service: 결제 완료(CONFIRMED) 상태 변경 알림
        publishStatusChangedEvent(order, prevStatus, Order.OrderStatus.CONFIRMED);

        log.info("[SAGA][STEP2] 완료 - orderId={}, status=CONFIRMED", orderId);
    }

    // ================================================================
    // SAGA 보상: 결제 실패 → 주문 취소 + 취소 알림 발행
    // ================================================================

    /**
     * payment-service로부터 결제 실패 수신
     * → Order CANCELLED 처리 (보상 트랜잭션)
     * → notification-service로 취소 알림
     * 호출 위치: OrderEventConsumer.consumePaymentResult()
     */
    @Transactional
    public void onPaymentFailed(UUID orderId, String reason) {
        log.warn("[SAGA][COMPENSATE] 결제 실패, 주문 취소 처리 - orderId={}, reason={}", orderId, reason);

        Order order = findOrder(orderId);
        Order.OrderStatus prevStatus = order.getStatus();

        // Order CANCELLED 처리 (보상)
        order.markPaymentFailed();
        order.markSagaCompensated();
        orderRepository.save(order);

        // notification-service: 결제 실패로 인한 취소 알림
        publishStatusChangedEvent(order, prevStatus, Order.OrderStatus.CANCELLED);

        log.warn("[SAGA][COMPENSATE] 완료 - orderId={}, status=CANCELLED", orderId);
    }

    // ================================================================
    // 주문 취소 → 결제 취소 + 배달 취소 + 취소 알림 발행
    // ================================================================

    /**
     * 고객 또는 관리자의 주문 취소 요청 처리
     * → payment-service: 결제 취소 요청 (결제가 진행된 경우)
     * → shipment-service: 배달 취소 요청 (CONFIRMED 상태인 경우)
     * → notification-service: 취소 알림
     * 호출 위치: OrderService.cancelOrder()
     */
    @Transactional
    public void onOrderCancelled(Order order, Order.OrderStatus prevStatus, String cancelledBy) {
        log.info("[SAGA][CANCEL] 주문 취소 처리 - orderId={}, prevStatus={}", order.getId(), prevStatus);

        boolean needsPaymentCancel = order.requiresPaymentCancellation()
                || prevStatus == Order.OrderStatus.CONFIRMED;
        boolean needsShipmentCancel = prevStatus == Order.OrderStatus.CONFIRMED;

        // payment-service / shipment-service로 취소 이벤트 발행
        OrderCancelledEvent cancelledEvent = OrderCancelledEvent.builder()
                .orderId(order.getId())
                .companyId(order.getCompanyId())
                .userName(order.getUserName())
                .cancelledBy(cancelledBy)
                .requiresPaymentCancellation(needsPaymentCancel)
                .requiresShipmentCancellation(needsShipmentCancel)
                .occurredAt(LocalDateTime.now())
                .build();
        eventPublisher.publishOrderCancelled(cancelledEvent);

        // notification-service: 취소 알림
        publishStatusChangedEvent(order, prevStatus, Order.OrderStatus.CANCELLED);

        log.info("[SAGA][CANCEL] 완료 - orderId={}, paymentCancel={}, shipmentCancel={}",
                order.getId(), needsPaymentCancel, needsShipmentCancel);
    }

    // ================================================================
    // 상태 변경 (COMPANY) → 알림 + 완료 시 리뷰 요청
    // ================================================================

    /**
     * COMPANY의 주문 상태 변경 후 호출
     * → notification-service: 상태 변경 알림 (항상 발행)
     * → notification-service: 리뷰 요청 알림 (COMPLETED 상태일 때만 추가 발행)
     * → shipment 완료 처리: SAGA 완료 마킹
     * 호출 위치: OrderService.updateOrderStatus()
     */
    @Transactional
    public void onOrderStatusUpdated(Order order, Order.OrderStatus prevStatus) {
        log.info("[SAGA] 주문 상태 변경 - orderId={}, {} → {}",
                order.getId(), prevStatus, order.getStatus());

        // notification-service: 상태 변경 알림 (모든 상태 변경 시)
        publishStatusChangedEvent(order, prevStatus, order.getStatus());

        // COMPLETED 상태가 되면 SAGA 완료 마킹 + 리뷰 요청 이벤트 발행
        if (order.getStatus() == Order.OrderStatus.COMPLETED) {
            order.markSagaCompleted();
            orderRepository.save(order);
            publishOrderCompletedEvent(order);
        }
    }

    // ================================================================
    // Private: 이벤트 발행 헬퍼
    // ================================================================

    /**
     * 모든 상태 변경 시 notification-service로 알림 이벤트 발행
     */
    private void publishStatusChangedEvent(Order order,
                                           Order.OrderStatus prevStatus,
                                           Order.OrderStatus currentStatus) {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId(order.getId())
                .userName(order.getUserName())
                .companyId(order.getCompanyId())
                .previousStatus(prevStatus.name())
                .currentStatus(currentStatus.name())
                .occurredAt(LocalDateTime.now())
                .build();
        eventPublisher.publishOrderStatusChanged(event);
    }

    /**
     * COMPLETED 상태 시 notification-service로 리뷰 요청 이벤트 발행
     */
    private void publishOrderCompletedEvent(Order order) {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId(order.getId())
                .companyId(order.getCompanyId())
                .userName(order.getUserName())
                .productNames(order.getOrderItems().stream()
                        .map(item -> item.getProductName())
                        .toList())
                .totalPrice(order.getTotalPrice())
                .completedAt(LocalDateTime.now())
                .build();
        eventPublisher.publishOrderCompleted(event);
        log.info("[SAGA] 리뷰 요청 이벤트 발행 완료 - orderId={}", order.getId());
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}
