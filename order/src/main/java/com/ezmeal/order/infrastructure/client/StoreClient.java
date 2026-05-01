package com.ezmeal.order.infrastructure.client;

import com.delivery.orderservice.infrastructure.client.dto.StoreInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "store-service", url = "${services.store.url}")
public interface StoreClient {

    @GetMapping("/api/v1/stores/by-name")
    StoreInfo getStoreByName(@RequestParam("name") String storeName);

    @GetMapping("/api/v1/stores/by-owner")
    StoreInfo getStoreByOwner(@RequestParam("username") String ownerUsername);
}
