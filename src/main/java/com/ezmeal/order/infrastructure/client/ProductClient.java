package com.ezmeal.order.infrastructure.client;

import com.ezmeal.common.response.CommonApiResponse;
import com.ezmeal.order.infrastructure.client.dto.BulkReserveRequest;
import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service", url = "${services.product.url}")
public interface ProductClient {

//    @PostMapping("/internal/v1/products/{productId}/order-quantity/reserve")
//    CommonApiResponse<Void> reserveOrderQuantity(@PathVariable(value="productId") UUID productId,
//                                                 @RequestBody ProductOrderCountRequest request
//    );


    // 추가: 여러 상품 재고를 한 번에 예약
    @PostMapping("/internal/v1/products/order-quantity/reserve-bulk")
    CommonApiResponse<Void> reserveOrderQuantityBulk(
            @RequestBody BulkReserveRequest request
    );



//    @PostMapping("/internal/v1/products/{productId}/order-quantity/restore")
//    CommonApiResponse<Void> restoreOrderQuantity(
//            @PathVariable UUID productId,
//            @RequestBody ProductOrderCountRequest request
//    );

    @GetMapping("/internal/v1/products/by-ids")
    List<ProductInfo> getProductsByIds(@RequestParam("ids") List<UUID> ids);
}
