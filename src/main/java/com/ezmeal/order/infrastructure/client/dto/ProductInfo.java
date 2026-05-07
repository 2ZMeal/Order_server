package com.ezmeal.order.infrastructure.client.dto;

import lombok.Getter;
import java.util.UUID;

@Getter
public class ProductInfo {
    private UUID productId;
    private UUID companyId;
    private String name;
    private Integer price;
}
