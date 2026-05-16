package com.ezmeal.order.presentation.controller;

import com.ezmeal.common.enums.Role;
import com.ezmeal.common.response.CommonApiResponse;
import com.ezmeal.common.security.principal.CustomUserPrincipal;
import com.ezmeal.order.infrastructure.client.CompanyClient;
import com.ezmeal.order.infrastructure.client.ProductClient;
import com.ezmeal.order.infrastructure.client.dto.ProductInfo;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(
        partitions = 1,
        topics = {"order.created"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class OrderCreateWithKafkaTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EmbeddedKafkaBroker embeddedKafka;

    // OrderService는 Mock 안 함 → 실제 빈 사용

    @MockitoBean
    ProductClient productClient;

    @MockitoBean
    CompanyClient companyClient;

    @Test
    @DisplayName("주문 생성 시 order.created 이벤트가 Kafka에 발행된다")
    void createOrder_publishesOrderCreatedEvent() throws Exception {

        // ── Kafka Consumer 세팅 ──────────────────────────────────────
        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("test-group", "true", embeddedKafka);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<>(consumerProps,
                        new StringDeserializer(), new StringDeserializer())
                        .createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "order.created");

        // ── Feign Client stub ────────────────────────────────────────
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID companyId = UUID.randomUUID();

        ProductInfo mockProduct = new ProductInfo();
        // ProductInfo가 setter 없으면 리플렉션으로 세팅 필요
        // (아래 방법 참고)

        given(productClient.getProductsByIds(any()))
                .willReturn(List.of(buildProductInfo(productId, companyId, "테스트 상품", 10000)));

        given(productClient.reserveOrderQuantity(any(), any()))
                .willReturn(CommonApiResponse.success(null));

        // ── 요청 ────────────────────────────────────────────────────
        CustomUserPrincipal principal =
                new CustomUserPrincipal("test-user-id", Role.USER, "test@test.com");

        String requestBody = """
                {
                  "address": "서울시 강남구 테헤란로 123",
                  "comment": "문 앞에 놔주세요",
                  "products": [
                    {
                      "productId": "00000000-0000-0000-0000-000000000001",
                      "quantity": 2
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("X-User-Id", "test-user-id")
                        .header("X-User-Roles", "USER")
                        .header("X-User-Email", "test@test.com")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                principal, null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        ))))
                .andExpect(status().isCreated())
                .andDo(print());

        // ── Kafka 메시지 발행 검증 ────────────────────────────────────
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));

        assertThat(records.isEmpty()).isFalse();

        String publishedMessage = records.iterator().next().value();
        System.out.println("발행된 Kafka 메시지: " + publishedMessage);

        // orderId, userId 포함 여부 검증
        assertThat(publishedMessage).contains("test-user-id");

        consumer.close();
    }

    // ProductInfo에 setter/생성자 없을 때 리플렉션으로 값 세팅
    private ProductInfo buildProductInfo(UUID productId, UUID companyId,
                                         String name, int price) throws Exception {
        ProductInfo info = new ProductInfo();
        setField(info, "productId", productId);
        setField(info, "companyId", companyId);
        setField(info, "name", name);
        setField(info, "price", price);
        return info;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
