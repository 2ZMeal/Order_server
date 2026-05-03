package com.ezmeal.order.application.dto.request;

import lombok.Getter;
import java.util.UUID;

@Getter
public class OrderSearchRequestDto {
    private UUID companyId;
    private String userName;
    private String status;
    private String productName;
    private Integer minAmount;
    private Integer maxAmount;
}
