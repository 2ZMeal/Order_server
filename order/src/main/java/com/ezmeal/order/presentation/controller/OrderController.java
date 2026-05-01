package com.ezmeal.order.presentation.controller;

import com.delivery.orderservice.application.dto.request.OrderRequestDto;
import com.delivery.orderservice.application.dto.request.OrderSearchRequestDto;
import com.delivery.orderservice.application.dto.response.OrderResponseDto;
import com.delivery.orderservice.application.service.OrderService;
import com.delivery.orderservice.domain.entity.Order;
import com.delivery.orderservice.presentation.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "주문", description = "주문 생성·조회·취소·상태 변경")
public class OrderController {

    private final OrderService orderService;

    // ── 조회 ──────────────────────────────────────────────────────

    @GetMapping("/list")
    @Operation(summary = "주문 목록 조회",
            description = "권한별 조회 - ADMIN/MANAGER: 전체, OWNER: 본인 가게, CUSTOMER: 본인 주문")
    public ResponseEntity<ApiResponse<Page<OrderResponseDto>>> getOrders(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                orderService.selectOrders(
                        userDetails.getUsername(),
                        extractRoles(userDetails),
                        validatePageSize(pageable))));
    }

    @PostMapping("/listsearch")
    @Operation(summary = "주문 검색 조회")
    public ResponseEntity<ApiResponse<Page<OrderResponseDto>>> searchOrders(
            @RequestBody OrderSearchRequestDto searchDto,
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                orderService.selectOrdersSearch(
                        searchDto,
                        userDetails.getUsername(),
                        extractRoles(userDetails),
                        validatePageSize(pageable))));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "주문 단건 조회")
    public ResponseEntity<ApiResponse<OrderResponseDto>> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.selectOrder(orderId)));
    }

    // ── 생성 ──────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "주문 생성",
            description = "주문 저장 후 payment-service로 결제 요청 이벤트 발행 (SAGA 시작)")
    public ResponseEntity<ApiResponse<OrderResponseDto>> createOrder(
            @RequestBody OrderRequestDto dto,
            @AuthenticationPrincipal UserDetails userDetails) {

        OrderResponseDto response = orderService.createOrder(userDetails.getUsername(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    // ── 취소 ──────────────────────────────────────────────────────

    @PatchMapping("/{orderId}/cancel")
    @Operation(summary = "주문 취소",
            description = """
                   고객: 주문 후 5분 이내 + READY/PENDING 상태만 가능
                   관리자: DELIVERING·COMPLETED 제외하고 취소 가능
                   → payment-service 결제 취소 + shipment-service 배달 취소(CONFIRMED 상태였을 때) 이벤트 발행
                   """)
    public ResponseEntity<ApiResponse<OrderResponseDto>> cancelOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<String> roles = extractRoles(userDetails);
        OrderResponseDto response = orderService.cancelOrder(orderId, userDetails.getUsername(), roles);
        return ResponseEntity.ok(ApiResponse.success("주문이 취소되었습니다.", response));
    }

    // ── 상태 변경 ─────────────────────────────────────────────────

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "주문 상태 변경",
            description = """
                   OWNER/MANAGER: 상태 전이 규칙에 따라 변경
                   - CONFIRMED → DELIVERING: shipment-service 배달 시작
                   - DELIVERING → COMPLETED: notification-service 리뷰 요청 알림 발행
                   모든 상태 변경 시 notification-service 상태 알림 발행
                   """)
    public ResponseEntity<ApiResponse<OrderResponseDto>> updateStatus(
            @PathVariable UUID orderId,
            @RequestParam Order.OrderStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {

        List<String> roles = extractRoles(userDetails);
        OrderResponseDto response = orderService.updateOrderStatus(
                orderId, status, userDetails.getUsername(), roles);

        return ResponseEntity.ok(
                ApiResponse.success("주문 상태가 " + status + "(으)로 변경되었습니다.", response));
    }

    // ── 유틸 ──────────────────────────────────────────────────────

    private List<String> extractRoles(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    private Pageable validatePageSize(Pageable pageable) {
        int size = pageable.getPageSize();
        if (size != 10 && size != 30 && size != 50) {
            return org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(), 10, pageable.getSort());
        }
        return pageable;
    }
}
