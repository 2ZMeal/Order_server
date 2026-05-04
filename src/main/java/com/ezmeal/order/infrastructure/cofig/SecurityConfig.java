package com.ezmeal.order.infrastructure.cofig;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity   // @PreAuthorize 활성화
public class SecurityConfig {
}
