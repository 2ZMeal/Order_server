package com.ezmeal.order.orderService;

import com.ezmeal.order.application.dto.request.OrderRequestDto;
import com.ezmeal.order.application.dto.request.OrderSearchRequestDto;
import com.ezmeal.order.application.dto.response.OrderResponseDto;
import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.application.service.OrderService;
import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.repository.OrderRepository;
import com.ezmeal.order.infrastructure.client.ProductClient;
import com.ezmeal.order.infrastructure.client.CompanyClient;
import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import com.ezmeal.order.infrastructure.client.dto.CompanyInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 테스트")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderSagaOrchestrator sagaOrchestrator;
    @Mock private CompanyClient companyClient;
    @Mock private ProductClient productClient;

    @InjectMocks
    private OrderService orderService;

    private UUID orderId;
    private UUID companyId;
    private Order order;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        order = createSampleOrder();
        pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
    }

    // ================================================================
    // selectOrders - 목록 조회
    // ================================================================

    @Nested
    @DisplayName("주문 목록 조회 (selectOrders)")
    class SelectOrders {

        @Test
        @DisplayName("ADMIN 권한: 전체 주문 조회")
        void selectOrders_AdminRole_FindsAll() {
            List<String> roles = List.of("ROLE_ADMIN");
            Page<Order> page = new PageImpl<>(List.of(order));
            given(orderRepository.findAll(pageable)).willReturn(page);

            Page<OrderResponseDto> result =
                    orderService.selectOrders("admin", roles, pageable);

            assertThat(result.getContent()).hasSize(1);
            then(orderRepository).should().findAll(pageable);
            then(orderRepository).should(never()).findByUserName(any(), any());
        }

        @Test
        @DisplayName("COMPANY 권한: 본인 가게 주문만 조회")
        void selectOrders_CompanyRole_FindsByCompanyId() {
            List<String> roles = List.of("ROLE_COMPANY");
            CompanyInfo company = createCompanyInfo();
            Page<Order> page = new PageImpl<>(List.of(order));

            given(companyClient.getCompanyByCompany("company")).willReturn(company);
            given(orderRepository.findByCompanyId(company.getCompanyId(), pageable)).willReturn(page);

            Page<OrderResponseDto> result =
                    orderService.selectOrders("company", roles, pageable);

            assertThat(result.getContent()).hasSize(1);
            then(orderRepository).should().findByCompanyId(company.getCompanyId(), pageable);
        }

        @Test
        @DisplayName("CUSTOMER 권한: 본인 주문만 조회")
        void selectOrders_CustomerRole_FindsByUsername() {
            List<String> roles = List.of("ROLE_CUSTOMER");
            Page<Order> page = new PageImpl<>(List.of(order));
            given(orderRepository.findByUserName("test_user", pageable)).willReturn(page);

            Page<OrderResponseDto> result =
                    orderService.selectOrders("test_user", roles, pageable);

            assertThat(result.getContent()).hasSize(1);
            then(orderRepository).should().findByUserName("test_user", pageable);
        }
    }

    // ================================================================
    // selectOrder - 단건 조회
    // ================================================================

    @Nested
    @DisplayName("주문 단건 조회 (selectOrder)")
    class SelectOrder {

        @Test
        @DisplayName("존재하는 주문 조회 성공")
        void selectOrder_ExistingOrder_ReturnsDto() {
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            OrderResponseDto result = orderService.selectOrder(orderId);

            assertThat(result.getOrderId()).isEqualTo(orderId);
            assertThat(result.getUserName()).isEqualTo("test_user");
        }

        @Test
        @DisplayName("존재하지 않는 주문 조회 시 예외 발생")
        void selectOrder_NotFound_ThrowsException() {
            given(orderRepository.findById(orderId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.selectOrder(orderId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }
    }

    // ================================================================
    // createOrder - 주문 생성 (SAGA 시작)
    // ================================================================

    @Nested
    @DisplayName("주문 생성 (createOrder)")
    class CreateOrder {

        private OrderRequestDto requestDto;
        private CompanyInfo companyInfo;
        private List<ProductInfo> products;

        @BeforeEach
        void setUpCreateOrder() {
            requestDto = createOrderRequestDto();
            companyInfo = createCompanyInfo();
            products = createProductInfos();
        }

        @Test
        @DisplayName("주문 생성 성공: Order 저장 후 SAGA 시작")
        void createOrder_Success_SavesOrderAndStartsSaga() {
            given(companyClient.getCompanyByName("맛있는 도시락")).willReturn(companyInfo);
            given(productClient.getProductsByNames(any())).willReturn(products);
            given(orderRepository.save(any())).willReturn(order);

            OrderResponseDto result = orderService.createOrder("test_user", requestDto);

            assertThat(result).isNotNull();
            then(orderRepository).should().save(any(Order.class));
            then(sagaOrchestrator).should().onOrderCreated(any(Order.class));
        }

        @Test
        @DisplayName("주문 생성 시 총 금액이 올바르게 계산된다 (가격 × 수량)")
        void createOrder_TotalPriceCalculated() {
            given(companyClient.getCompanyByName(any())).willReturn(companyInfo);
            given(productClient.getProductsByNames(any())).willReturn(products);

            ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
            given(orderRepository.save(orderCaptor.capture())).willReturn(order);

            orderService.createOrder("test_user", requestDto);

            // 도시락A(10000 × 2) + 도시락B(5000 × 1) = 25000
            assertThat(orderCaptor.getValue().getTotalPrice()).isEqualTo(25000);
        }

        @Test
        @DisplayName("존재하지 않는 상품명 포함 시 예외 발생")
        void createOrder_ProductNotFound_ThrowsException() {
            given(companyClient.getCompanyByName(any())).willReturn(companyInfo);
            given(productClient.getProductsByNames(any())).willReturn(
                    List.of() // 빈 리스트 반환 (상품 없음)
            );

            assertThatThrownBy(() -> orderService.createOrder("test_user", requestDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("상품을 찾을 수 없습니다");
        }

        @Test
        @DisplayName("주문 생성 성공 시 SAGA.onOrderCreated()가 1번 호출된다")
        void createOrder_SagaOnOrderCreatedCalledOnce() {
            given(companyClient.getCompanyByName(any())).willReturn(companyInfo);
            given(productClient.getProductsByNames(any())).willReturn(products);
            given(orderRepository.save(any())).willReturn(order);

            orderService.createOrder("test_user", requestDto);

            then(sagaOrchestrator).should(times(1)).onOrderCreated(any());
        }
    }

    // ================================================================
    // cancelOrder - 주문 취소
    // ================================================================

    @Nested
    @DisplayName("주문 취소 (cancelOrder)")
    class CancelOrder {

        @Test
        @DisplayName("CUSTOMER가 본인 PENDING 주문을 5분 이내 취소 성공")
        void cancelOrder_CustomerOwnPendingOrder_Success() {
            order.markPaymentRequested(); // PENDING 상태
            List<String> roles = List.of("ROLE_CUSTOMER");

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            OrderResponseDto result = orderService.cancelOrder(orderId, "test_user", roles);

            assertThat(result.getStatus()).isEqualTo("CANCELLED");
            then(sagaOrchestrator).should().onOrderCancelled(any(), any(), any());
        }

        @Test
        @DisplayName("CUSTOMER가 타인 주문 취소 시도 시 예외 발생")
        void cancelOrder_CustomerCancelsOtherOrder_ThrowsException() {
            List<String> roles = List.of("ROLE_CUSTOMER");
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    orderService.cancelOrder(orderId, "other_user", roles))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인의 주문만");
        }

        @Test
        @DisplayName("ADMIN은 타인 주문도 취소 가능")
        void cancelOrder_AdminCancelsAnyOrder_Success() {
            order.markPaymentRequested(); // PENDING
            List<String> roles = List.of("ROLE_ADMIN");

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            assertThatCode(() ->
                    orderService.cancelOrder(orderId, "admin_user", roles))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("CUSTOMER가 5분 경과 후 취소 시도 시 예외 발생")
        void cancelOrder_CustomerAfter5Min_ThrowsException() {
            // READY 상태지만 isCancellable()이 false가 되도록 createdAt을 과거로 설정
            Order expiredOrder = createExpiredOrder();
            List<String> roles = List.of("ROLE_CUSTOMER");

            given(orderRepository.findById(orderId)).willReturn(Optional.of(expiredOrder));

            assertThatThrownBy(() ->
                    orderService.cancelOrder(orderId, "test_user", roles))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("5분이 경과");
        }

        @Test
        @DisplayName("DELIVERING 상태 주문 취소 시 도메인에서 예외 발생")
        void cancelOrder_DeliveringStatus_ThrowsException() {
            order.markPaymentRequested();
            order.markPaymentCompleted("system");
            order.updateStatus(Order.OrderStatus.DELIVERING, "company"); // DELIVERING

            List<String> roles = List.of("ROLE_ADMIN");
            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

            assertThatThrownBy(() ->
                    orderService.cancelOrder(orderId, "admin", roles))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("배달 중인 주문");
        }
    }

    // ================================================================
    // updateOrderStatus - 상태 변경
    // ================================================================

    @Nested
    @DisplayName("주문 상태 변경 (updateOrderStatus)")
    class UpdateOrderStatus {

        @BeforeEach
        void confirmedOrder() {
            order.markPaymentRequested();
            order.markPaymentCompleted("system"); // CONFIRMED
        }

        @Test
        @DisplayName("COMPANY가 본인 가게 주문 상태 변경 성공 (CONFIRMED → DELIVERING)")
        void updateStatus_CompanyOwnCompany_Success() {
            List<String> roles = List.of("ROLE_COMPANY");
            CompanyInfo company = createCompanyInfo();

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(companyClient.getCompanyByCompany("company")).willReturn(company);
            given(orderRepository.save(any())).willReturn(order);

            OrderResponseDto result = orderService.updateOrderStatus(
                    orderId, Order.OrderStatus.DELIVERING, "company", roles);

            assertThat(result.getStatus()).isEqualTo("DELIVERING");
            then(sagaOrchestrator).should().onOrderStatusUpdated(any(), any(), any());
        }

        @Test
        @DisplayName("COMPANY가 타인 가게 주문 상태 변경 시 예외 발생")
        void updateStatus_CompanyOtherCompany_ThrowsException() {
            List<String> roles = List.of("ROLE_COMPANY");
            CompanyInfo otherCompany = new CompanyInfo();
            // 다른 companyId를 가진 company
            try {
                var f = CompanyInfo.class.getDeclaredField("companyId");
                f.setAccessible(true);
                f.set(otherCompany, UUID.randomUUID()); // order.companyId와 다름
            } catch (Exception e) { throw new RuntimeException(e); }

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(companyClient.getCompanyByCompany("other_company")).willReturn(otherCompany);

            assertThatThrownBy(() ->
                    orderService.updateOrderStatus(
                            orderId, Order.OrderStatus.DELIVERING, "other_company", roles))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("본인 가게의 주문만");
        }

        @Test
        @DisplayName("ADMIN은 모든 주문 상태 변경 가능")
        void updateStatus_AdminRole_Success() {
            List<String> roles = List.of("ROLE_ADMIN");

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            assertThatCode(() ->
                    orderService.updateOrderStatus(
                            orderId, Order.OrderStatus.DELIVERING, "admin", roles))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("COMPLETED 상태 변경 시 SAGA onOrderStatusUpdated가 호출된다")
        void updateStatus_ToCompleted_SagaCalled() {
            order.updateStatus(Order.OrderStatus.DELIVERING, "company");
            List<String> roles = List.of("ROLE_ADMIN");

            given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willReturn(order);

            orderService.updateOrderStatus(
                    orderId, Order.OrderStatus.COMPLETED, "admin", roles);

            then(sagaOrchestrator).should()
                    .onOrderStatusUpdated(any(), eq(Order.OrderStatus.DELIVERING), eq("admin"));
        }
    }

    // ================================================================
    // 헬퍼 메서드
    // ================================================================

    private Order createSampleOrder() {
        Order o = Order.create("test_user", companyId,
                "서울시 강남구", 25000, "문 앞에 놔주세요", Order.OrderType.ONLINE);
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(o, orderId);
        } catch (Exception e) { throw new RuntimeException(e); }
        return o;
    }

    private Order createExpiredOrder() {
        // createdAt을 10분 전으로 설정해 isCancellable() = false
        Order o = createSampleOrder();
        try {
            var field = Order.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(o, java.time.LocalDateTime.now().minusMinutes(10));
        } catch (Exception e) { throw new RuntimeException(e); }
        return o;
    }

    private CompanyInfo createCompanyInfo() {
        CompanyInfo company = new CompanyInfo();
        try {
            var f1 = CompanyInfo.class.getDeclaredField("companyId");
            f1.setAccessible(true);
            f1.set(company, companyId);
            var f2 = CompanyInfo.class.getDeclaredField("name");
            f2.setAccessible(true);
            f2.set(company, "맛있는 도시락");
        } catch (Exception e) { throw new RuntimeException(e); }
        return company;
    }

    private List<ProductInfo> createProductInfos() {
        ProductInfo p1 = new ProductInfo();
        ProductInfo p2 = new ProductInfo();
        try {
            var nameF = ProductInfo.class.getDeclaredField("name");
            var priceF = ProductInfo.class.getDeclaredField("price");
            nameF.setAccessible(true);
            priceF.setAccessible(true);

            nameF.set(p1, "도시락A"); priceF.set(p1, 10000);
            nameF.set(p2, "도시락B"); priceF.set(p2, 5000);
        } catch (Exception e) { throw new RuntimeException(e); }
        return List.of(p1, p2);
    }

    private OrderRequestDto createOrderRequestDto() {
        // 도시락A × 2 = 20000, 도시락B × 1 = 5000 → 합계 25000
        return new OrderRequestDto() {{
            try {
                var companyNameF = OrderRequestDto.class.getDeclaredField("companyName");
                var addressF   = OrderRequestDto.class.getDeclaredField("address");
                var productsF  = OrderRequestDto.class.getDeclaredField("products");
                companyNameF.setAccessible(true); companyNameF.set(this, "맛있는 도시락");
                addressF.setAccessible(true);   addressF.set(this, "서울시 강남구");

                var itemClass = OrderRequestDto.ProductItem.class;
                var nameF  = itemClass.getDeclaredField("productName");
                var quantF = itemClass.getDeclaredField("quantity");
                nameF.setAccessible(true); quantF.setAccessible(true);

                var item1 = itemClass.getDeclaredConstructor().newInstance();
                nameF.set(item1, "도시락A"); quantF.set(item1, 2);

                var item2 = itemClass.getDeclaredConstructor().newInstance();
                nameF.set(item2, "도시락B"); quantF.set(item2, 1);

                productsF.setAccessible(true);
                productsF.set(this, List.of(item1, item2));
            } catch (Exception e) { throw new RuntimeException(e); }
        }};
    }
}
