package com.ezmeal.order.infrastructure.client.dto;

import lombok.Getter;
import java.util.UUID;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductInfo {
    private UUID productId;
    private UUID companyId;
    private String name;
    private Integer price;
}
