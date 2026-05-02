package com.ezmeal.order.domainLayer;

import com.ezmeal.order.domain.entity.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Order 도메인 엔티티 테스트")
class OrderTest {

    private UUID companyId;
    private String userName;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        userName = "test_user";
    }

    // ================================================================
    // Order.create() - 정적 팩토리
    // ================================================================

    @Nested
    @DisplayName("주문 생성 (Order.create)")
    class CreateOrder {

        @Test
        @DisplayName("주문 생성 시 초기 상태는 READY, sagaStatus는 ORDER_CREATED")
        void create_InitialStatus() {
            Order order = Order.create(userName, companyId,
                    "서울시 강남구", 25000, "문 앞에 놔주세요", Order.OrderType.ONLINE);

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.READY);
            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.ORDER_CREATED);
            assertThat(order.getUserName()).isEqualTo(userName);
            assertThat(order.getCompanyId()).isEqualTo(companyId);
            assertThat(order.getTotalPrice()).isEqualTo(25000);
            assertThat(order.getCreatedAt()).isNotNull();
            assertThat(order.getCreatedBy()).isEqualTo(userName);
        }
    }

    // ================================================================
    // SAGA 상태 전이
    // ================================================================

    @Nested
    @DisplayName("SAGA 상태 전이")
    class SagaStatus {

        @Test
        @DisplayName("markPaymentRequested: READY → PENDING, PAYMENT_REQUESTED")
        void markPaymentRequested() {
            Order order = createReadyOrder();
            order.markPaymentRequested();

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.PAYMENT_REQUESTED);
        }

        @Test
        @DisplayName("markPaymentCompleted: PENDING → CONFIRMED, PAYMENT_COMPLETED")
        void markPaymentCompleted() {
            Order order = createReadyOrder();
            order.markPaymentRequested();
            order.markPaymentCompleted("system");

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.PAYMENT_COMPLETED);
            assertThat(order.getUpdatedBy()).isEqualTo("system");
        }

        @Test
        @DisplayName("markPaymentFailed: 주문 CANCELLED + soft delete 처리")
        void markPaymentFailed() {
            Order order = createReadyOrder();
            order.markPaymentRequested();
            order.markPaymentFailed("system");

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.PAYMENT_FAILED);
            assertThat(order.getDeletedAt()).isNotNull();
            assertThat(order.getDeletedBy()).isEqualTo("system");
        }

        @Test
        @DisplayName("markSagaCompleted: sagaStatus → SAGA_COMPLETED")
        void markSagaCompleted() {
            Order order = createReadyOrder();
            order.markPaymentCompleted("system");
            order.markSagaCompleted();

            assertThat(order.getSagaStatus()).isEqualTo(Order.SagaStatus.SAGA_COMPLETED);
        }
    }

    // ================================================================
    // cancel()
    // ================================================================

    @Nested
    @DisplayName("주문 취소 (cancel)")
    class CancelOrder {

        @Test
        @DisplayName("PENDING 상태 주문은 취소 가능")
        void cancel_PendingOrder_Success() {
            Order order = createReadyOrder();
            order.markPaymentRequested(); // PENDING

            assertThatCode(() -> order.cancel("test_user"))
                    .doesNotThrowAnyException();

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
            assertThat(order.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 취소된 주문은 다시 취소 불가")
        void cancel_AlreadyCancelled_ThrowsException() {
            Order order = createReadyOrder();
            order.cancel("test_user");

            assertThatThrownBy(() -> order.cancel("test_user"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 취소된 주문");
        }

        @Test
        @DisplayName("DELIVERING 상태 주문은 취소 불가")
        void cancel_DeliveringOrder_ThrowsException() {
            Order order = createConfirmedOrder();
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");

            assertThatThrownBy(() -> order.cancel("test_user"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("배달 중인 주문");
        }

        @Test
        @DisplayName("COMPLETED 상태 주문은 취소 불가")
        void cancel_CompletedOrder_ThrowsException() {
            Order order = createConfirmedOrder();
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");
            order.updateStatus(Order.OrderStatus.COMPLETED, "company");

            assertThatThrownBy(() -> order.cancel("test_user"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이미 완료된 주문");
        }
    }

    // ================================================================
    // updateStatus()
    // ================================================================

    @Nested
    @DisplayName("주문 상태 변경 (updateStatus)")
    class UpdateStatus {

        @Test
        @DisplayName("정상 전이: CONFIRMED → DELIVERING")
        void updateStatus_ConfirmedToDelivering_Success() {
            Order order = createConfirmedOrder();
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.DELIVERING);
            assertThat(order.getUpdatedBy()).isEqualTo("company");
        }

        @Test
        @DisplayName("정상 전이: DELIVERING → COMPLETED")
        void updateStatus_DeliveringToCompleted_Success() {
            Order order = createConfirmedOrder();
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");
            order.updateStatus(Order.OrderStatus.COMPLETED, "company");

            assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.COMPLETED);
        }

        @Test
        @DisplayName("비정상 전이: CONFIRMED → COMPLETED 는 불가")
        void updateStatus_InvalidTransition_ThrowsException() {
            Order order = createConfirmedOrder();

            assertThatThrownBy(() -> order.updateStatus(Order.OrderStatus.COMPLETED, "company"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("상태 변경 불가");
        }

        @Test
        @DisplayName("비정상 전이: DELIVERING → PENDING 은 불가")
        void updateStatus_BackwardTransition_ThrowsException() {
            Order order = createConfirmedOrder();
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");

            assertThatThrownBy(() -> order.updateStatus(Order.OrderStatus.PENDING, "company"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("COMPLETED 상태에서 상태 변경 불가")
        void updateStatus_AfterCompleted_ThrowsException() {
            Order order = createConfirmedOrder();
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");
            order.updateStatus(Order.OrderStatus.COMPLETED, "company");

            assertThatThrownBy(() -> order.updateStatus(Order.OrderStatus.CANCELLED, "company"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("완료되었거나 취소된");
        }
    }

    // ================================================================
    // isCancellable() / requiresXxxCancellation()
    // ================================================================

    @Nested
    @DisplayName("취소 가능 여부 판단")
    class CancellableCheck {

        @Test
        @DisplayName("PENDING + 5분 이내 → 취소 가능")
        void isCancellable_PendingWithin5Min_True() {
            Order order = createReadyOrder();
            order.markPaymentRequested(); // PENDING
            assertThat(order.isCancellable()).isTrue();
        }

        @Test
        @DisplayName("CONFIRMED 상태 → isCancellable false (5분 이내여도 고객 직접 취소 불가)")
        void isCancellable_ConfirmedStatus_False() {
            Order order = createConfirmedOrder();
            assertThat(order.isCancellable()).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED 상태에서 취소 시 shipment 취소 필요")
        void requiresShipmentCancellation_WhenConfirmed_True() {
            Order order = createConfirmedOrder();
            assertThat(order.requiresShipmentCancellation()).isTrue();
        }

        @Test
        @DisplayName("PENDING 상태에서 취소 시 shipment 취소 불필요 (배달 요청 안 감)")
        void requiresShipmentCancellation_WhenPending_False() {
            Order order = createReadyOrder();
            order.markPaymentRequested();
            assertThat(order.requiresShipmentCancellation()).isFalse();
        }

        @Test
        @DisplayName("CONFIRMED 상태에서 취소 시 payment 취소 필요")
        void requiresPaymentCancellation_WhenConfirmed_True() {
            Order order = createConfirmedOrder();
            assertThat(order.requiresPaymentCancellation()).isTrue();
        }

        @Test
        @DisplayName("PENDING 상태에서 취소 시 payment 취소 필요")
        void requiresPaymentCancellation_WhenPending_True() {
            Order order = createReadyOrder();
            order.markPaymentRequested();
            assertThat(order.requiresPaymentCancellation()).isTrue();
        }
    }

    // ================================================================
    // 헬퍼 메서드
    // ================================================================

    private Order createReadyOrder() {
        return Order.create(userName, companyId,
                "서울시 강남구", 25000, "문 앞에 놔주세요", Order.OrderType.ONLINE);
    }

    private Order createConfirmedOrder() {
        Order order = createReadyOrder();
        order.markPaymentRequested();
        order.markPaymentCompleted("system");
        return order;
    }
}
