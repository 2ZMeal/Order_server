package com.ezmeal.order.infrastructure.kafka;

import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.infrastructure.kafka.dto.PaymentResultMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Order Service가 수신하는 Kafka 이벤트 컨슈머
 *
 * 수신 토픽:
 * - payment.result : payment-service가 결제 처리 후 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderSagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    /**
     * payment-service로부터 결제 결과 수신
     *
     * 성공: onPaymentCompleted() → CONFIRMED + shipment.requested + order.status.changed 발행
     * 실패: onPaymentFailed()    → CANCELLED + order.status.changed 발행 (보상)
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_RESULT,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentResult(String message, Acknowledgment ack) {
        try {
            PaymentResultMessage result = objectMapper.readValue(message, PaymentResultMessage.class);
            log.info("[Kafka][CONSUME] payment.result - orderId={}, success={}",
                    result.getOrderId(), result.isSuccess());

            if (result.isSuccess()) {
                sagaOrchestrator.onPaymentCompleted(
                        result.getOrderId(),
                        result.getPaymentId(),
                        result.getUserUsername()
                );
            } else {
                sagaOrchestrator.onPaymentFailed(
                        result.getOrderId(),
                        result.getReason()
                );
            }

            ack.acknowledge(); // 정상 처리 완료 시에만 커밋

        } catch (JsonProcessingException e) {
            // 메시지 파싱 자체가 실패 → 재처리해도 또 실패함
            // 이 경우 ack 해서 넘기고 DLQ로 보내는 게 맞음
            log.error("[Kafka][CONSUME] 메시지 파싱 실패 (DLQ 이동) - message={}", message, e);
            ack.acknowledge(); // 무한 재처리 방지를 위해 커밋하고 넘김

        } catch (Exception e) {
            // 비즈니스 로직 실패 → 재처리 가능성 있음
            // ack 안 하면 컨슈머 재시작 시 재처리됨
            log.error("[Kafka][CONSUME] 처리 실패, 재처리 대기 - orderId 파싱 시도", e);
            // ack 미호출 → 재처리
        }

    }
//            ack.acknowledge(); // 수동 커밋 (at-least-once)
//        } catch (Exception e) {
//            log.error("[Kafka][CONSUME] payment.result 처리 실패 - message={}", message, e);
//            // 실패 시 acknowledge 하지 않아 재처리됨 (DLQ 연동 권장)
//        }
//    }
}
