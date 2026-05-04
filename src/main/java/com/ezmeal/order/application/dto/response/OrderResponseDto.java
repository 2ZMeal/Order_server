package com.ezmeal.order.application.dto.response;

import com.ezmeal.order.domain.entity.Order;
import com.ezmeal.order.domain.entity.OrderItem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class OrderResponseDto {

    private UUID orderId;
    private String userId;
    private UUID companyId;
    private String deliveryAddress;
    private String status;
    private String sagaStatus;
    private Integer totalPrice;
    private String requestNote;
    private String orderType;
    private List<OrderItemDto> items;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;

    public static OrderResponseDto from(Order order) {
        return OrderResponseDto.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .companyId(order.getCompanyId())
                .deliveryAddress(order.getDeliveryAddress())
                .status(order.getStatus().name())
                .sagaStatus(order.getSagaStatus() != null ? order.getSagaStatus().name() : null)
                .totalPrice(order.getTotalPrice())
                .requestNote(order.getRequestNote())
                .orderType(order.getOrderType().name())
                .items(order.getOrderItems().stream()
                        .map(OrderItemDto::from)
                        .collect(Collectors.toList()))
                .createdAt(order.getCreatedAt())
                .modifiedAt(order.getModifiedAt())
                .build();
    }

    @Getter
    @Builder
    public static class OrderItemDto {
        private String productName;
        private Integer productPrice;
        private Integer quantity;

        public static OrderItemDto from(OrderItem item) {
            return OrderItemDto.builder()
                    .productName(item.getProductName())
                    .productPrice(item.getProductPrice())
                    .quantity(item.getQuantity())
                    .build();
        }
    }
}
