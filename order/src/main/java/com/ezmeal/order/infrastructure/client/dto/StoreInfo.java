package com.ezmeal.order.infrastructure.client.dto;

import lombok.Getter;
import java.util.UUID;

@Getter
public class StoreInfo {
    private UUID storeId;
    private String name;
    private String ownerUsername;
}
