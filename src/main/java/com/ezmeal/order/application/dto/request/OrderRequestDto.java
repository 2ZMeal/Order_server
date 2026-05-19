package com.ezmeal.order.application.dto.request;

import java.util.UUID;
import lombok.Getter;
import java.util.List;

@Getter
public class OrderRequestDto {
//    private String companyName;

    private String address;
    private String comment;
    private List<ProductItem> products;

    @Getter
    public static class ProductItem {
        private UUID productId;
        private Integer quantity;
    }
}
