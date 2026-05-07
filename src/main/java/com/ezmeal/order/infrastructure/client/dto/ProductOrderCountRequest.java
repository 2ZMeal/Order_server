package com.ezmeal.order.infrastructure.client.dto;

import java.util.UUID;

public record ProductOrderCountRequest(


    Integer quantity,
    UUID orderId,
    String productId
) {
    }
