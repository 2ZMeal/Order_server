package com.ezmeal.order.infrastructure.kafka.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class StockRestoreResultMessage {
    private UUID orderId;
    private boolean success;
    private String reason;      // 실패 시 사유
    private String productId;   // 실패한 상품 ID (실패 시)
}
