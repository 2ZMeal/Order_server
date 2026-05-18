package com.ezmeal.order.presentation.controller;

import com.ezmeal.common.enums.Role;
import com.ezmeal.common.security.principal.CustomUserPrincipal;
import com.ezmeal.order.application.dto.response.OrderResponseDto;
import com.ezmeal.order.application.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderService orderService;

    @Test
    @DisplayName("주문 생성 API 테스트")
    void createOrder() throws Exception {

        // given
        CustomUserPrincipal principal =
                new CustomUserPrincipal("test-user-id", Role.USER, "test@test.com");

        OrderResponseDto mockResponse = OrderResponseDto.builder()
                .orderId(UUID.randomUUID())
                .userId("test-user-id")
                .status("READY")
                .sagaStatus("ORDER_CREATED")
                .totalPrice(20000)
                .build();

        given(orderService.createOrder(any(), any())).willReturn(mockResponse);

        String requestBody = """
            {
              "address": "서울시 강남구 테헤란로 123",
              "comment": "문 앞에 놔주세요",
              "products": [
                { "productId": "00000000-0000-0000-0000-000000000001", "quantity": 2 }
              ]
            }
            """;

        // when & then
        mockMvc.perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .with(authentication(
                                        new UsernamePasswordAuthenticationToken(
                                                principal, null,
                                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                                        )
                                ))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.sagaStatus").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.data.totalPrice").value(20000))
                .andDo(print());
    }
}
