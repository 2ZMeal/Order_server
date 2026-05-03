package com.ezmeal.order.kafkaConsumer;

import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.infrastructure.kafka.OrderEventConsumer;
import com.ezmeal.order.infrastructure.kafka.dto.PaymentResultMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventConsumer 테스트")
class OrderEventConsumerTest {

    @Mock private OrderSagaOrchestrator sagaOrchestrator;
    @Mock private Acknowledgment acknowledgment;

    @InjectMocks
    private OrderEventConsumer consumer;

    private ObjectMapper objectMapper;
    private UUID orderId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new OrderEventConsumer(sagaOrchestrator, objectMapper);
        orderId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
    }

    // ================================================================
    // consumePaymentResult
    // ================================================================

    @Nested
    @DisplayName("payment.result 메시지 소비")
    class ConsumePaymentResult {

        @Test
        @DisplayName("결제 성공 메시지 수신 시 onPaymentCompleted 호출 + ack")
        void consumePaymentResult_Success_CallsOnPaymentCompleted() throws Exception {
            PaymentResultMessage message = buildSuccessMessage();
            String json = objectMapper.writeValueAsString(message);

            consumer.consumePaymentResult(json, acknowledgment);

            then(sagaOrchestrator).should().onPaymentCompleted(
                    eq(orderId), eq(paymentId), eq("test_user"));
            then(acknowledgment).should().acknowledge();
        }

        @Test
        @DisplayName("결제 실패 메시지 수신 시 onPaymentFailed 호출 + ack")
        void consumePaymentResult_Fail_CallsOnPaymentFailed() throws Exception {
            PaymentResultMessage message = buildFailMessage("잔액 부족");
            String json = objectMapper.writeValueAsString(message);

            consumer.consumePaymentResult(json, acknowledgment);

            then(sagaOrchestrator).should().onPaymentFailed(eq(orderId), eq("잔액 부족"));
            then(acknowledgment).should().acknowledge();
        }

        @Test
        @DisplayName("JSON 파싱 실패 시 ack하지 않아 재처리 대기")
        void consumePaymentResult_InvalidJson_NoAck() {
            String invalidJson = "{ invalid json }";

            consumer.consumePaymentResult(invalidJson, acknowledgment);

            // 비즈니스 로직 호출 안 됨
            then(sagaOrchestrator).should(never()).onPaymentCompleted(any(), any(), any());
            then(sagaOrchestrator).should(never()).onPaymentFailed(any(), any());
            // ack 미호출 → 재처리 대기
            then(acknowledgment).should(never()).acknowledge();
        }

        @Test
        @DisplayName("onPaymentCompleted 처리 중 예외 발생 시 ack하지 않아 재처리 대기")
        void consumePaymentResult_BusinessException_NoAck() throws Exception {
            PaymentResultMessage message = buildSuccessMessage();
            String json = objectMapper.writeValueAsString(message);

            willThrow(new RuntimeException("DB 오류"))
                    .given(sagaOrchestrator).onPaymentCompleted(any(), any(), any());

            consumer.consumePaymentResult(json, acknowledgment);

            then(acknowledgment).should(never()).acknowledge();
        }
    }

    // ================================================================
    // 헬퍼
    // ================================================================

    private PaymentResultMessage buildSuccessMessage() {
        PaymentResultMessage msg = new PaymentResultMessage();
        try {
            setField(msg, "orderId", orderId);
            setField(msg, "paymentId", paymentId);
            setField(msg, "userName", "test_user");
            setField(msg, "success", true);
        } catch (Exception e) { throw new RuntimeException(e); }
        return msg;
    }

    private PaymentResultMessage buildFailMessage(String reason) {
        PaymentResultMessage msg = new PaymentResultMessage();
        try {
            setField(msg, "orderId", orderId);
            setField(msg, "success", false);
            setField(msg, "reason", reason);
        } catch (Exception e) { throw new RuntimeException(e); }
        return msg;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
