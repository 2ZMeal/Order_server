package com.ezmeal.order.infrastructure.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BulkReserveRequest {

    private UUID orderId;
    private List<ProductReserveItem> items;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductReserveItem {
        private UUID productId;
        private Integer quantity;
    }
}
