package com.ezmeal.order.application.dto.request;

import lombok.Getter;
import java.util.List;

@Getter
public class OrderRequestDto {
    private String storeName;
    private String address;
    private String comment;
    private List<ProductItem> products;

    @Getter
    public static class ProductItem {
        private String productName;
        private Integer quantity;
    }
}
