package com.ezmeal.order.application.service;

import com.ezmeal.common.exception.CustomException;
import com.ezmeal.common.response.CommonApiResponse;
import com.ezmeal.common.security.principal.CustomUserPrincipal;
import com.ezmeal.order.application.dto.request.OrderRequestDto;
import com.ezmeal.order.application.dto.response.OrderResponseDto;
import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.entity.OrderItem;
import com.ezmeal.order.domain.exception.OrderErrorCode;
import com.ezmeal.order.domain.repository.OrderRepository;
import com.ezmeal.order.infrastructure.client.ProductClient;
import com.ezmeal.order.infrastructure.client.dto.BulkReserveRequest;
import com.ezmeal.order.infrastructure.client.dto.BulkReserveRequest.ProductReserveItem;
import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 트랜잭션 범위를 최소화하기 위해 OrderService에서 분리
 * - HTTP 호출(getProductsByIds)은 OrderService에서 트랜잭션 밖에서 처리
 * - 이 클래스에서는 DB 저장 + 재고 예약(동기) + Kafka 발행만 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderSagaOrchestrator sagaOrchestrator;

    @Transactional
    public OrderResponseDto saveOrderAndReserveStock(
            CustomUserPrincipal principal,
            OrderRequestDto dto,
            Map<UUID, ProductInfo> productMap,
            int totalPrice) {

        // Order 생성 및 저장
        Order order = Order.create(
                principal.getUserId(),
                dto.getAddress(),
                totalPrice,
                dto.getComment()
        );

        dto.getProducts().forEach(item -> {
            ProductInfo p = productMap.get(item.getProductId());
            order.getOrderItems().add(
                    OrderItem.create(order, p.getCompanyId(), p.getProductId(),
                            p.getName(), p.getPrice(), item.getQuantity())
            );
        });

        Order savedOrder = orderRepository.save(order);

        // 재고 예약 (동기 Feign 유지)
//        for (OrderRequestDto.ProductItem product : dto.getProducts()) {

        // ── 변경 코드 (HTTP 1번으로 전체 예약) ───────────────────────
        List<ProductReserveItem> reserveItems = dto.getProducts().stream()
                .map(product -> new BulkReserveRequest.ProductReserveItem(
                        product.getProductId(),
                        product.getQuantity()
                ))
                .toList();

        BulkReserveRequest bulkRequest = new BulkReserveRequest(savedOrder.getId(), reserveItems);

        try {
            CommonApiResponse<Void> response = productClient.reserveOrderQuantityBulk(bulkRequest);

            if (response == null || !"SUCCESS".equals(response.getCode())) {
                log.warn("[재고 예약 실패] orderId={}, 롤백 시작", savedOrder.getId());
                cancelOrderInternal(savedOrder, principal.getUserId());
                throw new CustomException(OrderErrorCode.STOCK_RESERVE_FAILED);
            }

            log.info("[재고 예약 성공] orderId={}, 상품 수={}", savedOrder.getId(), reserveItems.size());

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("[재고 예약 오류] orderId={}, 롤백 시작 - error={}", savedOrder.getId(), e.getMessage());
            cancelOrderInternal(savedOrder, principal.getUserId());
            throw new CustomException(OrderErrorCode.STOCK_RESERVE_FAILED);
        }


        // 모든 재고 예약 성공 → SAGA 시작
        sagaOrchestrator.onOrderCreated(savedOrder);

        log.info("[OrderTransactionService] 주문 생성 완료 - orderId={}", savedOrder.getId());
        return OrderResponseDto.from(savedOrder);
    }

    private void cancelOrderInternal(Order order, String cancelledBy) {
        order.cancel(cancelledBy);
        orderRepository.save(order);
        log.info("[OrderTransactionService] 재고 예약 실패로 주문 취소 - orderId={}", order.getId());
    }
}
