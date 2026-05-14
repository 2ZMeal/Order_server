package com.ezmeal.order.infrastructure.client.dto;

import lombok.Getter;
import java.util.UUID;

@Getter
public class CompanyInfo {
    private UUID companyId;
    private String name;
    private String managerUserId;
}
