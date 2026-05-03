package com.ezmeal.order.Integration;

import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.application.service.OrderService;
import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.event.*;
import com.ezmeal.order.domain.repository.OrderRepository;
import com.ezmeal.order.infrastructure.client.ProductClient;
import com.ezmeal.order.infrastructure.client.CompanyClient;
import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import com.ezmeal.order.infrastructure.client.dto.CompanyInfo;
import com.ezmeal.order.infrastructure.kafka.OrderEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

/**
 * SAGA 전체 흐름 통합 테스트
 *
 * 실제 DB/Kafka 없이 전체 흐름을 검증합니다.
 * OrderService → OrderSagaOrchestrator → OrderEventPublisher 의 연동을 확인합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("[통합] SAGA 전체 흐름 테스트")
class OrderSagaIntegrationTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderEventPublisher eventPublisher;
    @Mock private CompanyClient companyClient;
    @Mock private ProductClient productClient;

    private OrderSagaOrchestrator sagaOrchestrator;
    private OrderService orderService;

    private UUID orderId;
    private UUID companyId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        orderId   = UUID.randomUUID();
        companyId   = UUID.randomUUID();
        paymentId = UUID.randomUUID();

        // 실제 객체로 연결 (Spy 없이 직접 주입)
        sagaOrchestrator = new OrderSagaOrchestrator(orderRepository, eventPublisher);
        orderService     = new OrderService(orderRepository, sagaOrchestrator, companyClient, productClient);
    }

    // ================================================================
    // 시나리오 1: 정상 주문 완료 플로우
    // ================================================================

    @Test
    @DisplayName("[SAGA 정상] 주문생성 → 결제완료 → 배달중 → 완료 전체 흐름")
    void fullSagaFlow_HappyPath() throws Exception {
        // ── Given ──
        Order savedOrder = createOrderWithId();
        given(companyClient.getCompanyByName(any())).willReturn(createCompanyInfo());
        given(productClient.getProductsByNames(any())).willReturn(createProducts());
        given(orderRepository.save(any())).willReturn(savedOrder);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(savedOrder));

        // ── STEP 1: 주문 생성 ──
        orderService.createOrder("test_user", createOrderRequestDto());

        assertThat(savedOrder.getStatus()).isEqualTo(Order.OrderStatus.PENDING);
        // order.created 발행 확인
        then(eventPublisher).should(atLeastOnce()).publishOrderCreated(any());
        // order.status.changed(READY→PENDING) 발행 확인
        ArgumentCaptor<OrderStatusChangedEvent> statusCaptor =
                ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        then(eventPublisher).should(atLeastOnce()).publishOrderStatusChanged(statusCaptor.capture());
        assertThat(statusCaptor.getAllValues())
                .anyMatch(e -> e.getCurrentStatus().equals("PENDING"));

        // ── STEP 2: 결제 완료 수신 ──
        sagaOrchestrator.onPaymentCompleted(orderId, paymentId, "system");

        assertThat(savedOrder.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        // shipment.requested 발행 확인
        then(eventPublisher).should().publishShipmentRequested(any());
        // order.status.changed(PENDING→CONFIRMED) 발행 확인
        then(eventPublisher).should(atLeastOnce()).publishOrderStatusChanged(
                argThat(e -> e.getCurrentStatus().equals("CONFIRMED")));

        // ── STEP 3: 배달 시작 ──
        savedOrder.updateStatus(Order.OrderStatus.DELIVERING, "company");
        sagaOrchestrator.onOrderStatusUpdated(savedOrder, Order.OrderStatus.CONFIRMED, "company");

        then(eventPublisher).should(atLeastOnce()).publishOrderStatusChanged(
                argThat(e -> e.getCurrentStatus().equals("DELIVERING")));

        // ── STEP 4: 배달 완료 + 리뷰 요청 ──
        savedOrder.updateStatus(Order.OrderStatus.COMPLETED, "company");
        sagaOrchestrator.onOrderStatusUpdated(savedOrder, Order.OrderStatus.DELIVERING, "company");

        assertThat(savedOrder.getSagaStatus()).isEqualTo(Order.SagaStatus.SAGA_COMPLETED);
        // order.completed(리뷰 요청) 발행 확인
        then(eventPublisher).should().publishOrderCompleted(any());
    }

    // ================================================================
    // 시나리오 2: 결제 실패 보상 플로우
    // ================================================================

    @Test
    @DisplayName("[SAGA 보상] 주문생성 → 결제실패 → 주문 취소 + 취소 알림")
    void sagaFlow_PaymentFailed_Compensation() throws Exception {
        Order savedOrder = createOrderWithId();
        savedOrder.markPaymentRequested(); // PENDING

        given(companyClient.getCompanyByName(any())).willReturn(createCompanyInfo());
        given(productClient.getProductsByNames(any())).willReturn(createProducts());
        given(orderRepository.save(any())).willReturn(savedOrder);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(savedOrder));

        // STEP 1: 주문 생성
        orderService.createOrder("test_user", createOrderRequestDto());

        // STEP 2: 결제 실패 수신 (보상)
        sagaOrchestrator.onPaymentFailed(orderId, "잔액 부족");

        assertThat(savedOrder.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
        assertThat(savedOrder.getSagaStatus()).isEqualTo(Order.SagaStatus.SAGA_COMPENSATED);

        // 취소 알림 발행 확인
        then(eventPublisher).should(atLeastOnce()).publishOrderStatusChanged(
                argThat(e -> e.getCurrentStatus().equals("CANCELLED")));

        // shipment 요청은 발행 안 됨
        then(eventPublisher).should(never()).publishShipmentRequested(any());
    }

    // ================================================================
    // 시나리오 3: CONFIRMED 상태 주문 취소 플로우
    // ================================================================

    @Test
    @DisplayName("[SAGA 취소] CONFIRMED 주문 취소 → 결제취소 + 배달취소 이벤트 발행")
    void cancelOrder_ConfirmedStatus_BothCancellationEventsPublished() throws Exception {
        Order savedOrder = createOrderWithId();
        savedOrder.markPaymentRequested();
        savedOrder.markPaymentCompleted("system"); // CONFIRMED

        given(orderRepository.findById(orderId)).willReturn(Optional.of(savedOrder));
        given(orderRepository.save(any())).willReturn(savedOrder);

        // 관리자가 CONFIRMED 주문 취소
        orderService.cancelOrder(orderId, "admin", List.of("ROLE_ADMIN"));

        // order.cancelled 발행 확인
        ArgumentCaptor<OrderCancelledEvent> cancelCaptor =
                ArgumentCaptor.forClass(OrderCancelledEvent.class);
        then(eventPublisher).should().publishOrderCancelled(cancelCaptor.capture());

        OrderCancelledEvent cancelledEvent = cancelCaptor.getValue();
        assertThat(cancelledEvent.isRequiresPaymentCancellation()).isTrue();
        assertThat(cancelledEvent.isRequiresShipmentCancellation()).isTrue();

        // 취소 알림 발행 확인
        then(eventPublisher).should(atLeastOnce()).publishOrderStatusChanged(
                argThat(e -> e.getCurrentStatus().equals("CANCELLED")));
    }

    // ================================================================
    // 시나리오 4: DELIVERING 상태 취소 시도 (불가)
    // ================================================================

    @Test
    @DisplayName("[취소 불가] DELIVERING 상태 주문은 취소할 수 없다")
    void cancelOrder_DeliveringStatus_ThrowsException() {
        Order savedOrder = createOrderWithId();
        savedOrder.markPaymentRequested();
        savedOrder.markPaymentCompleted("system");
        savedOrder.updateStatus(Order.OrderStatus.DELIVERING, "company");

        given(orderRepository.findById(orderId)).willReturn(Optional.of(savedOrder));

        assertThatThrownBy(() ->
                orderService.cancelOrder(orderId, "admin", List.of("ROLE_ADMIN")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("배달 중인 주문");

        // 취소 이벤트 미발행 확인
        then(eventPublisher).should(never()).publishOrderCancelled(any());
    }

    // ================================================================
    // 헬퍼
    // ================================================================

    private Order createOrderWithId() {
        Order o = Order.create("test_user", companyId,
                "서울시 강남구", 25000, "요청사항", Order.OrderType.ONLINE);
        try {
            var f = Order.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(o, orderId);
        } catch (Exception e) { throw new RuntimeException(e); }
        return o;
    }

    private CompanyInfo createCompanyInfo() {
        CompanyInfo s = new CompanyInfo();
        try {
            var f1 = CompanyInfo.class.getDeclaredField("companyId");
            var f2 = CompanyInfo.class.getDeclaredField("name");
            f1.setAccessible(true); f1.set(s, companyId);
            f2.setAccessible(true); f2.set(s, "맛있는 도시락");
        } catch (Exception e) { throw new RuntimeException(e); }
        return s;
    }

    private List<ProductInfo> createProducts() {
        ProductInfo p1 = new ProductInfo();
        ProductInfo p2 = new ProductInfo();
        try {
            var nf = ProductInfo.class.getDeclaredField("name");
            var pf = ProductInfo.class.getDeclaredField("price");
            nf.setAccessible(true); pf.setAccessible(true);
            nf.set(p1, "도시락A"); pf.set(p1, 10000);
            nf.set(p2, "도시락B"); pf.set(p2, 5000);
        } catch (Exception e) { throw new RuntimeException(e); }
        return List.of(p1, p2);
    }

    private com.ezmeal.order.application.dto.request.OrderRequestDto createOrderRequestDto() {
        var dto = new com.ezmeal.order.application.dto.request.OrderRequestDto();
        try {
            var itemClass = com.ezmeal.order.application.dto.request.OrderRequestDto.ProductItem.class;

            var item1 = itemClass.getDeclaredConstructor().newInstance();
            var nf = itemClass.getDeclaredField("productName");
            var qf = itemClass.getDeclaredField("quantity");
            nf.setAccessible(true); qf.setAccessible(true);
            nf.set(item1, "도시락A"); qf.set(item1, 2);

            var item2 = itemClass.getDeclaredConstructor().newInstance();
            nf.set(item2, "도시락B"); qf.set(item2, 1);

            var sf = dto.getClass().getDeclaredField("companyName");
            var af = dto.getClass().getDeclaredField("address");
            var pf = dto.getClass().getDeclaredField("products");
            sf.setAccessible(true); sf.set(dto, "맛있는 도시락");
            af.setAccessible(true); af.set(dto, "서울시 강남구");
            pf.setAccessible(true); pf.set(dto, List.of(item1, item2));
        } catch (Exception e) { throw new RuntimeException(e); }
        return dto;
    }
}
