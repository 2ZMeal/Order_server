package com.ezmeal.order.application.service;

import com.ezmeal.common.enums.Role;
import com.ezmeal.common.exception.CustomException;
import com.ezmeal.common.security.principal.CustomUserPrincipal;
import com.ezmeal.order.application.dto.request.OrderRequestDto;
import com.ezmeal.order.application.dto.request.OrderSearchRequestDto;
import com.ezmeal.order.application.dto.response.OrderResponseDto;
import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.entity.OrderItem;
import com.ezmeal.order.domain.exception.OrderErrorCode;
import com.ezmeal.order.domain.repository.OrderRepository;
import com.ezmeal.order.infrastructure.client.CompanyClient;
import com.ezmeal.order.infrastructure.client.dto.CompanyInfo;
import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import com.ezmeal.order.infrastructure.client.ProductClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSagaOrchestrator sagaOrchestrator;
    private final CompanyClient companyClient;
    private final ProductClient productClient;

    // ========================
    // 조회
    // ========================

    public Page<OrderResponseDto> selectOrders(CustomUserPrincipal principal, Pageable pageable) {
        Page<Order> page = switch (principal.getRole()) {   // Role enum switch 로 교체
            case ADMIN   -> orderRepository.findAll(pageable);

            case COMPANY -> {                               // ROLE_OWNER + ROLE_MANAGER → COMPANY
                CompanyInfo company = companyClient.getCompanyByCompany(principal.getUserId());
                yield orderRepository.findByCompanyId(company.getCompanyId(), pageable);
            }
            case USER    -> orderRepository.findByUserId(principal.getUserId(), pageable);  // ROLE_CUSTOMER → USER
        };
        return page.map(OrderResponseDto::from);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY', 'USER')")
    public Page<OrderResponseDto> selectOrdersSearch(
            OrderSearchRequestDto dto, CustomUserPrincipal principal, Pageable pageable)
    {

        UUID companyId      = null;
        String userId = null;           // customerUsername → customerId

        switch (principal.getRole()) {
            case ADMIN   -> {
                companyId    = dto.getCompanyId();
                userId = dto.getUserId();   // getCustomerUsername() → getCustomerId()
            }
            case COMPANY -> {
                CompanyInfo store = companyClient.getCompanyByCompany(principal.getUserId());
                companyId = store.getCompanyId();
            }
            case USER    -> userId = principal.getUserId();
        }

        Order.OrderStatus status = (dto.getStatus() != null)
                ? Order.OrderStatus.valueOf(dto.getStatus()) : null;


        return orderRepository.searchWithFilters(
                companyId, userId, status,
                dto.getProductName(), dto.getMinAmount(), dto.getMaxAmount(), pageable
        ).map(OrderResponseDto::from);
    }

    public OrderResponseDto selectOrder(UUID orderId) {
        return OrderResponseDto.from(findOrder(orderId));
    }

    // ========================
    // 주문 생성 → SAGA 시작
    // ========================

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'USER')")
    public OrderResponseDto createOrder(CustomUserPrincipal principal, OrderRequestDto dto) {


        // 1. 상품 정보 조회 (product-service FeignClient)
        List<String> productIds = dto.getProducts().stream()
                .map(OrderRequestDto.ProductItem::getProductId)
                .toList();
        List<ProductInfo> products = productClient.getProductsByIds(productIds);

        Map<UUID, ProductInfo> productMap = products.stream()
                .collect(Collectors.toMap(ProductInfo::getProductId, p -> p));

        // 2. 총 금액 계산
        int totalPrice = dto.getProducts().stream()
                .mapToInt(item -> {
                    ProductInfo p = productMap.get(item.getProductId());
                    if (p == null) {
                        throw new CustomException(OrderErrorCode.PRODUCT_NOT_FOUND);
                    }
                    return p.getPrice() * item.getQuantity();
                }).sum();

        // 3. Order 엔티티 생성
        Order order = Order.create(
                principal.getUserId(),
                dto.getAddress(),
                totalPrice,
                dto.getComment()
        );

        // 4. OrderItem 생성 및 연관관계 설정
        dto.getProducts().forEach(item -> {
            ProductInfo p = productMap.get(item.getProductId());
            if (p == null) throw new CustomException(OrderErrorCode.PRODUCT_NOT_FOUND);
            order.getOrderItems().add(
                    OrderItem.create(order, p.getCompanyId(), p.getName(), p.getPrice(), item.getQuantity())
            );
        });

        Order savedOrder = orderRepository.save(order);

        // 5. SAGA 시작 (결제 요청 이벤트 발행)
        sagaOrchestrator.onOrderCreated(savedOrder);

        log.info("[OrderService] 주문 생성 완료 - orderId={}", savedOrder.getId());
        return OrderResponseDto.from(savedOrder);
    }

    // ========================
    // 주문 취소
    // ========================

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'COMPANY')")
    public OrderResponseDto cancelOrder(UUID orderId, CustomUserPrincipal principal) {
        Order order = findOrder(orderId);
        Role role = principal.getRole();

        // 고객 본인 주문인지 확인 (관리자는 예외)
        if (role == Role.USER && !order.getUserId().equals(principal.getUserId())) {
            throw new CustomException(OrderErrorCode.ORDER_CANCEL_FORBIDDEN);   // CustomException 으로 교체
        }

        // 고객은 5분 이내만 취소 가능, 관리자는 상태 제한만 적용
        if (role == Role.USER && !order.isCancellable()) {
            throw new CustomException(OrderErrorCode.ORDER_CANCEL_TIME_EXPIRED);  // CustomException 으로 교체
        }

        Order.OrderStatus prevStatus = order.getStatus();

        // 도메인 취소 처리 (상태 검증 포함 - DELIVERING 이후는 불가)
        order.cancel(principal.getUserId());
        orderRepository.save(order);

        // SAGA: 결제 취소 + 배달 취소 + 취소 알림 이벤트 발행
        sagaOrchestrator.onOrderCancelled(order, prevStatus, principal.getUserId());

        log.info("[OrderService] 주문 취소 완료 - orderId={}, prevStatus={}", orderId, prevStatus);
        return OrderResponseDto.from(order);
    }

    // ========================
    // 주문 상태 변경 (COMPANY)
    // ========================

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY')")
    public OrderResponseDto updateOrderStatus(UUID orderId, Order.OrderStatus newStatus,
                                              CustomUserPrincipal principal) {
        Order order = findOrder(orderId);

        Order.OrderStatus prevStatus = order.getStatus();

        // 도메인 상태 변경 (전이 규칙 검증 포함)
        order.updateStatus(newStatus);
        orderRepository.save(order);

        // SAGA: 상태 알림 + COMPLETED 시 리뷰 요청 이벤트 발행
        sagaOrchestrator.onOrderStatusUpdated(order, prevStatus);

        log.info("[OrderService] 주문 상태 변경 - orderId={}, {} → {}", orderId, prevStatus, newStatus);
        return OrderResponseDto.from(order);
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(OrderErrorCode.ORDER_NOT_FOUND));
    }

}
