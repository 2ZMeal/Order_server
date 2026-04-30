package com.delivery.orderservice.application.dto.request;

import lombok.Getter;
import java.util.UUID;

@Getter
public class OrderSearchRequestDto {
    private UUID storeId;
    private String customerUsername;
    private String status;
    private String productName;
    private Integer minAmount;
    private Integer maxAmount;
}
