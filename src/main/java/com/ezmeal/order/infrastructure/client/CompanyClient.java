package com.ezmeal.order.infrastructure.client;

import com.ezmeal.order.infrastructure.client.dto.CompanyInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "company-service", url = "${services.company.url}")
public interface CompanyClient {

    @GetMapping("/api/v1/companies/by-company")
    CompanyInfo getCompanyByCompany(@RequestParam("managerUserId") String managerUserId);
}
