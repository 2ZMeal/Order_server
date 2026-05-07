package com.ezmeal.order.infrastructure.client;

import com.ezmeal.common.response.CommonApiResponse;
import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import com.ezmeal.order.infrastructure.client.dto.ProductOrderCountRequest;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service", url = "${services.product.url}")
public interface ProductClient {

    @PostMapping("/internal/products/{productId}/order-quantity/reserve")
    CommonApiResponse<Void> reserveOrderQuantity(/*@PathVariable List<String> productIds,*/
                                                 @RequestBody ProductOrderCountRequest request
    );

    @PostMapping("/internal/products/{productId}/order-quantity/restore")
    CommonApiResponse<Void> restoreOrderQuantity(
    @PathVariable UUID productId,
    @RequestBody ProductOrderCountRequest request
    );

    @GetMapping("/api/v1/products/by-ids")
    List<ProductInfo> getProductsByIds(@RequestParam("ids") List<String> ids);
}
