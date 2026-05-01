package com.ezmeal.order.application.service;

import com.ezmeal.order.application.dto.request.OrderRequestDto;
import com.ezmeal.order.application.dto.request.OrderSearchRequestDto;
import com.ezmeal.order.application.dto.response.OrderResponseDto;
import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.entity.OrderItem;
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

    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY', 'USER')")
    public Page<OrderResponseDto> selectOrders(String username, List<String> roles, Pageable pageable) {
        Page<Order> page;
        if (roles.contains("ROLE_ADMIN")) {
            page = orderRepository.findAll(pageable);
        } else if (roles.contains("ROLE_COMPANY")) {
            CompanyInfo company = companyClient.getCompanyByCompany(username);
            page = orderRepository.findByCompanyId(company.getCompanyId(), pageable);
        } else {
            page = orderRepository.findByUserName(username, pageable);
        }
        return page.map(OrderResponseDto::from);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY', 'USER')")
    public Page<OrderResponseDto> selectOrdersSearch(
            OrderSearchRequestDto dto, String username, List<String> roles, Pageable pageable) {

        UUID companyId = null;
        String userUsername = null;

        if (roles.contains("ROLE_ADMIN")) {
            // 관리자: DTO의 조건 그대로 사용
            companyId = dto.getCompanyId();
            userUsername = dto.getUserName();
        } else if (roles.contains("ROLE_COMPANY")) {
            // 사장님: 본인 가게 ID로 고정
            CompanyInfo company = companyClient.getCompanyByCompany(username);
            companyId = company.getCompanyId();
        } else {
            // 고객: 본인 username으로 고정
            userUsername = username;
        }

        Order.OrderStatus status = (dto.getStatus() != null)
                ? Order.OrderStatus.valueOf(dto.getStatus()) : null;

        return orderRepository.searchWithFilters(
                companyId, userUsername, status,
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
    public OrderResponseDto createOrder(String username, OrderRequestDto dto) {
        // 1. 가게 정보 조회 (company-service FeignClient)
        CompanyInfo company = companyClient.getCompanyByName(dto.getCompanyName());

        // 2. 상품 정보 조회 (product-service FeignClient)
        List<String> productNames = dto.getProducts().stream()
                .map(OrderRequestDto.ProductItem::getProductName)
                .toList();
        List<ProductInfo> products = productClient.getProductsByNames(productNames);

        Map<String, ProductInfo> productMap = products.stream()
                .collect(Collectors.toMap(ProductInfo::getName, p -> p));

        // 3. 총 금액 계산
        int totalPrice = dto.getProducts().stream()
                .mapToInt(item -> {
                    ProductInfo p = productMap.get(item.getProductName());
                    if (p == null) {
                        throw new IllegalArgumentException("상품을 찾을 수 없습니다: " + item.getProductName());
                    }
                    return p.getPrice() * item.getQuantity();
                }).sum();

        // 4. Order 엔티티 생성
        Order order = Order.create(
                username,
                company.getCompanyId(),
                dto.getAddress(),
                totalPrice,
                dto.getComment(),
                Order.OrderType.ONLINE
        );

        // 5. OrderItem 생성 및 연관관계 설정
        dto.getProducts().forEach(item -> {
            ProductInfo p = productMap.get(item.getProductName());
            order.getOrderItems().add(
                    OrderItem.create(order, p.getName(), p.getPrice(), item.getQuantity(), username)
            );
        });

        Order savedOrder = orderRepository.save(order);

        // 6. SAGA 시작 (결제 요청 이벤트 발행)
        sagaOrchestrator.onOrderCreated(savedOrder);

        log.info("[OrderService] 주문 생성 완료 - orderId={}, username={}", savedOrder.getId(), username);
        return OrderResponseDto.from(savedOrder);
    }

    // ========================
    // 주문 취소
    // ========================

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'USER', 'COMPANY')")
    public OrderResponseDto cancelOrder(UUID orderId, String username, List<String> roles) {
        Order order = findOrder(orderId);

        // 고객 본인 주문인지 확인 (관리자는 예외)
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        if (!isAdmin && !order.getUserName().equals(username)) {
            throw new IllegalArgumentException("본인의 주문만 취소할 수 있습니다.");
        }

        // 고객은 5분 이내만 취소 가능, 관리자는 상태 제한만 적용
        if (!isAdmin && !order.isCancellable()) {
            throw new IllegalStateException("주문 후 5분이 경과하였거나 이미 처리된 주문입니다.");
        }

        Order.OrderStatus prevStatus = order.getStatus();

        // 도메인 취소 처리 (상태 검증 포함 - DELIVERING 이후는 불가)
        order.cancel(username);
        orderRepository.save(order);

        // SAGA: 결제 취소 + 배달 취소 + 취소 알림 이벤트 발행
        sagaOrchestrator.onOrderCancelled(order, prevStatus, username);

        log.info("[OrderService] 주문 취소 완료 - orderId={}, prevStatus={}", orderId, prevStatus);
        return OrderResponseDto.from(order);
    }

    // ========================
    // 주문 상태 변경 (COMPANY)
    // ========================

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY')")
    public OrderResponseDto updateOrderStatus(UUID orderId, Order.OrderStatus newStatus,
                                              String username, List<String> roles) {
        Order order = findOrder(orderId);

        // COMPANY는 본인 가게 주문만 변경 가능
        boolean isStaff = roles.contains("ROLE_ADMIN");
        if (!isStaff) {
            CompanyInfo company = companyClient.getCompanyByCompany(username);
            if (!order.getCompanyId().equals(company.getCompanyId())) {
                throw new IllegalArgumentException("본인 가게의 주문만 상태를 변경할 수 있습니다.");
            }
        }

        Order.OrderStatus prevStatus = order.getStatus();

        // 도메인 상태 변경 (전이 규칙 검증 포함)
        order.updateStatus(newStatus, username);
        orderRepository.save(order);

        // SAGA: 상태 알림 + COMPLETED 시 리뷰 요청 이벤트 발행
        sagaOrchestrator.onOrderStatusUpdated(order, prevStatus, username);

        log.info("[OrderService] 주문 상태 변경 - orderId={}, {} → {}", orderId, prevStatus, newStatus);
        return OrderResponseDto.from(order);
    }

    private Order findOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));
    }
}
