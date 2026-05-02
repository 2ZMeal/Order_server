package com.ezmeal.order.sagaOrchestrator;

import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.entity.OrderItem;
import com.ezmeal.order.domain.event.*;
import com.ezmeal.order.domain.repository.OrderRepository;
import com.ezmeal.order.infrastructure.kafka.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaOrchestrator 테스트")
class OrderSagaOrchestratorTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher eventPublisher;

    @InjectMocks
    private OrderSagaOrchestrator sagaOrchestrator;

    private UUID orderId;
    private UUID companyId;
    private UUID paymentId;
    private Order order;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        order = createSampleOrder();
    }

    // ================================================================
    // onOrderCreated - STEP 1
    // ================================================================

    @Nested
    @DisplayName("STEP 1: onOrderCreated - 주문 생성 → 결제 요청")
    class OnOrderCreated {

        @Test
        @DisplayName("주문 생성 시 Order 상태가 PENDING으로 변경된다")
        void onOrderCreated_ChangesStatusToPending() {
            given(orderRepository.save(any())).willReturn(order);

            sagaOrchestrator.onOrderCreated(order);

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.PAYMENT_REQUESTED);
        }

        @Test
        @DisplayName("payment-service로 OrderCreatedEvent가 발행된다")
        void onOrderCreated_PublishesOrderCreatedEvent() {
            given(orderRepository.save(any())).willReturn(order);

            sagaOrchestrator.onOrderCreated(order);

            // OrderCreatedEvent 발행 검증
            ArgumentCaptor<OrderCreatedEvent> captor =
                    ArgumentCaptor.forClass(OrderCreatedEvent.class);
            then(eventPublisher).should().publishOrderCreated(captor.capture());

            OrderCreatedEvent publishedEvent = captor.getValue();
            assertThat(publishedEvent.getOrderId()).isEqualTo(order.getId());
            assertThat(publishedEvent.getCompanyId()).isEqualTo(order.getCompanyId());
            assertThat(publishedEvent.getTotalPrice()).isEqualTo(order.getTotalPrice());
        }

        @Test
        @DisplayName("notification-service로 상태 변경 이벤트(READY→PENDING)가 발행된다")
        void onOrderCreated_PublishesStatusChangedEvent() {
            given(orderRepository.save(any())).willReturn(order);

            sagaOrchestrator.onOrderCreated(order);

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            then(eventPublisher).should().publishOrderStatusChanged(captor.capture());

            OrderStatusChangedEvent event = captor.getValue();
            assertThat(event.getPreviousStatus()).isEqualTo("READY");
            assertThat(event.getCurrentStatus()).isEqualTo("PENDING");
        }
    }

    // ================================================================
    // onPaymentCompleted - STEP 2
    // ================================================================

    @Nested
    @DisplayName("STEP 2: onPaymentCompleted - 결제 완료 → 배달 요청")
    class OnPaymentCompleted {

        @BeforeEach
        void pendingOrder() {
            order.markPaymentRequested(); // PENDING 상태로
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);
        }

        @Test
        @DisplayName("결제 완료 시 Order 상태가 CONFIRMED로 변경된다")
        void onPaymentCompleted_ChangesStatusToConfirmed() {
            sagaOrchestrator.onPaymentCompleted(orderId, paymentId, "test_user");

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.PAYMENT_COMPLETED);
        }

        @Test
        @DisplayName("shipment-service로 ShipmentRequestedEvent가 발행된다")
        void onPaymentCompleted_PublishesShipmentRequestedEvent() {
            sagaOrchestrator.onPaymentCompleted(orderId, paymentId, "test_user");

            ArgumentCaptor<ShipmentRequestedEvent> captor =
                    ArgumentCaptor.forClass(ShipmentRequestedEvent.class);
            then(eventPublisher).should().publishShipmentRequested(captor.capture());

            ShipmentRequestedEvent event = captor.getValue();
            assertThat(event.getOrderId()).isEqualTo(orderId);
            assertThat(event.getCompanyId()).isEqualTo(companyId);
            assertThat(event.getDeliveryAddress()).isEqualTo("서울시 강남구");
        }

        @Test
        @DisplayName("notification-service로 상태 변경 이벤트(PENDING→CONFIRMED)가 발행된다")
        void onPaymentCompleted_PublishesStatusChangedEvent() {
            sagaOrchestrator.onPaymentCompleted(orderId, paymentId, "test_user");

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            then(eventPublisher).should().publishOrderStatusChanged(captor.capture());

            OrderStatusChangedEvent event = captor.getValue();
            assertThat(event.getPreviousStatus()).isEqualTo("PENDING");
            assertThat(event.getCurrentStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        @DisplayName("존재하지 않는 orderId면 예외가 발생한다")
        void onPaymentCompleted_OrderNotFound_ThrowsException() {
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    sagaOrchestrator.onPaymentCompleted(orderId, paymentId, "test_user"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문 없음");
        }
    }

    // ================================================================
    // onPaymentFailed - SAGA 보상
    // ================================================================

    @Nested
    @DisplayName("SAGA 보상: onPaymentFailed - 결제 실패 → 주문 취소")
    class OnPaymentFailed {

        @BeforeEach
        void pendingOrder() {
            order.markPaymentRequested();
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);
        }

        @Test
        @DisplayName("결제 실패 시 Order 상태가 CANCELLED로 변경된다")
        void onPaymentFailed_ChangesStatusToCancelled() {
            sagaOrchestrator.onPaymentFailed(orderId, "잔액 부족");

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.SAGA_COMPENSATED);
        }

        @Test
        @DisplayName("notification-service로 취소 알림 이벤트가 발행된다")
        void onPaymentFailed_PublishesStatusChangedEvent() {
            sagaOrchestrator.onPaymentFailed(orderId, "잔액 부족");

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            then(eventPublisher).should().publishOrderStatusChanged(captor.capture());

            assertThat(captor.getValue().getCurrentStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("결제 실패 시 ShipmentRequestedEvent는 발행되지 않는다")
        void onPaymentFailed_NoShipmentEvent() {
            sagaOrchestrator.onPaymentFailed(orderId, "잔액 부족");

            then(eventPublisher).should(never()).publishShipmentRequested(any());
        }
    }

    // ================================================================
    // onOrderCancelled - 취소 이벤트
    // ================================================================

    @Nested
    @DisplayName("onOrderCancelled - 주문 취소 이벤트 발행")
    class OnOrderCancelled {

        @Test
        @DisplayName("CONFIRMED 상태 취소: payment + shipment 모두 취소 이벤트 발행")
        void onOrderCancelled_ConfirmedStatus_BothCancellationFlags() {
            order.markPaymentRequested();
            order.markPaymentCompleted("system"); // CONFIRMED

            Order.OrderStatus prevStatus = Order.OrderStatus.CONFIRMED;
            order.cancel("test_user");

            sagaOrchestrator.onOrderCancelled(order, prevStatus, "test_user");

            ArgumentCaptor<OrderCancelledEvent> captor =
                    ArgumentCaptor.forClass(OrderCancelledEvent.class);
            then(eventPublisher).should().publishOrderCancelled(captor.capture());

            OrderCancelledEvent event = captor.getValue();
            assertThat(event.isRequiresPaymentCancellation()).isTrue();
            assertThat(event.isRequiresShipmentCancellation()).isTrue();
            assertThat(event.getCancelledBy()).isEqualTo("test_user");
        }

        @Test
        @DisplayName("PENDING 상태 취소: payment 취소만 필요, shipment 취소 불필요")
        void onOrderCancelled_PendingStatus_OnlyPaymentCancellation() {
            order.markPaymentRequested(); // PENDING
            Order.OrderStatus prevStatus = Order.OrderStatus.PENDING;
            order.cancel("test_user");

            sagaOrchestrator.onOrderCancelled(order, prevStatus, "test_user");

            ArgumentCaptor<OrderCancelledEvent> captor =
                    ArgumentCaptor.forClass(OrderCancelledEvent.class);
            then(eventPublisher).should().publishOrderCancelled(captor.capture());

            OrderCancelledEvent event = captor.getValue();
            assertThat(event.isRequiresPaymentCancellation()).isTrue();
            assertThat(event.isRequiresShipmentCancellation()).isFalse();
        }

        @Test
        @DisplayName("취소 시 notification-service로 취소 알림 이벤트가 발행된다")
        void onOrderCancelled_PublishesStatusChangedEvent() {
            order.markPaymentRequested();
            Order.OrderStatus prevStatus = Order.OrderStatus.PENDING;
            order.cancel("test_user");

            sagaOrchestrator.onOrderCancelled(order, prevStatus, "test_user");

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            then(eventPublisher).should().publishOrderStatusChanged(captor.capture());

            assertThat(captor.getValue().getCurrentStatus()).isEqualTo("CANCELLED");
        }
    }

    // ================================================================
    // onOrderStatusUpdated - 상태 변경 알림 + 리뷰 요청
    // ================================================================

    @Nested
    @DisplayName("onOrderStatusUpdated - 상태 변경 알림 및 리뷰 요청")
    class OnOrderStatusUpdated {

        @BeforeEach
        void confirmedOrder() {
            order.markPaymentRequested();
            order.markPaymentCompleted("system"); // CONFIRMED
            given(orderRepository.save(any())).willReturn(order);
        }

        @Test
        @DisplayName("모든 상태 변경 시 notification-service로 상태 변경 이벤트가 발행된다")
        void onOrderStatusUpdated_AlwaysPublishesStatusChangedEvent() {
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");

            sagaOrchestrator.onOrderStatusUpdated(
                    order, Order.OrderStatus.CONFIRMED, "company");

            ArgumentCaptor<OrderStatusChangedEvent> captor =
                    ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
            then(eventPublisher).should().publishOrderStatusChanged(captor.capture());

            OrderStatusChangedEvent event = captor.getValue();
            assertThat(event.getPreviousStatus()).isEqualTo("CONFIRMED");
            assertThat(event.getCurrentStatus()).isEqualTo("DELIVERING");
        }

        @Test
        @DisplayName("COMPLETED 상태 시 리뷰 요청 이벤트(OrderCompletedEvent)가 추가 발행된다")
        void onOrderStatusUpdated_WhenCompleted_PublishesOrderCompletedEvent() {
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");
            order.updateStatus(Order.OrderStatus.COMPLETED, "company");

            sagaOrchestrator.onOrderStatusUpdated(
                    order, Order.OrderStatus.DELIVERING, "company");

            // OrderCompletedEvent 발행 검증
            ArgumentCaptor<OrderCompletedEvent> captor =
                    ArgumentCaptor.forClass(OrderCompletedEvent.class);
            then(eventPublisher).should().publishOrderCompleted(captor.capture());

            OrderCompletedEvent event = captor.getValue();
            assertThat(event.getOrderId()).isEqualTo(order.getId());
            assertThat(event.getUserName()).isEqualTo("test_user");
        }

        @Test
        @DisplayName("COMPLETED 상태 시 sagaStatus가 SAGA_COMPLETED로 변경된다")
        void onOrderStatusUpdated_WhenCompleted_MarksSagaCompleted() {
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");
            order.updateStatus(Order.OrderStatus.COMPLETED, "company");

            sagaOrchestrator.onOrderStatusUpdated(
                    order, Order.OrderStatus.DELIVERING, "company");

            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.SAGA_COMPLETED);
        }

        @Test
        @DisplayName("DELIVERING 상태 변경 시 OrderCompletedEvent는 발행되지 않는다")
        void onOrderStatusUpdated_WhenDelivering_NoCompletedEvent() {
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");

            sagaOrchestrator.onOrderStatusUpdated(
                    order, Order.OrderStatus.CONFIRMED, "company");

            then(eventPublisher).should(never()).publishOrderCompleted(any());
        }
    }

    // ================================================================
    // 헬퍼
    // ================================================================

    private Order createSampleOrder() {
        Order o = Order.create("test_user", companyId,
                "서울시 강남구", 25000, "문 앞에 놔주세요", Order.OrderType.ONLINE);
        // reflection으로 id 주입 (테스트 전용)
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(o, orderId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return o;
    }
}