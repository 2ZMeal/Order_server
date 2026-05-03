package com.ezmeal.order.infrastructure.client;

import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-service", url = "${services.product.url}")
public interface ProductClient {

    @GetMapping("/api/v1/products/by-names")
    List<ProductInfo> getProductsByNames(@RequestParam("names") List<String> names);
}
