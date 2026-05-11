package com.ezmeal.order.infrastructure.kafka;

import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.domain.dlq.DlqEventRecord;
import com.ezmeal.order.infrastructure.kafka.dto.PaymentResultMessage;
import com.ezmeal.order.infrastructure.kafka.dto.StockRestoreResultMessage;
import com.ezmeal.order.infrastructure.persistence.DlqEventJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
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
    private final DlqEventJpaRepository dlqEventJpaRepository;

    /**
     * payment-service로부터 결제 결과 수신
     * <p>
     * 성공: onPaymentCompleted() → CONFIRMED + shipment.requested + order.status.changed 발행 실패: onPaymentFailed()    →
     * CANCELLED + order.status.changed 발행 (보상)
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_RESULT,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentResult(String message) {
        try {
            PaymentResultMessage result = objectMapper.readValue(message, PaymentResultMessage.class);
            log.info("[Kafka][CONSUME] payment.result - orderId={}, success={}",
                    result.getOrderId(), result.isSuccess());

            if (result.isSuccess()) {
                sagaOrchestrator.onPaymentCompleted(
                        result.getOrderId(),
                        result.getPaymentId()
                );
            } else {
                sagaOrchestrator.onPaymentFailed(
                        result.getOrderId(),
                        result.getReason()
                );
            }

//        } catch (JsonProcessingException e) {
//            // 메시지 파싱 자체가 실패 → 재처리해도 또 실패함
//            // 이 경우 ack 해서 넘기고 DLQ로 보내는 게 맞음
//            log.error("[Kafka][CONSUME] 메시지 파싱 실패 (DLQ 이동) - message={}", message, e);
//            ack.acknowledge(); // 무한 재처리 방지를 위해 커밋하고 넘김
//
//        } catch (Exception e) {
//            // 비즈니스 로직 실패 → 재처리 가능성 있음
//            // ack 안 하면 컨슈머 재시작 시 재처리됨
//            log.error("[Kafka][CONSUME] 처리 실패, 재처리 대기 - orderId 파싱 시도", e);
//            // ack 미호출 → 재처리
//        }
//
//    }

            // ack 제거 (공통 모듈 ContainerFactory AckMode 에 위임)

        } catch (Exception e) {
            log.error("[Kafka][CONSUME] payment.result 처리 실패 - message={}", message, e);
            throw new RuntimeException("payment.result 처리 실패", e);
            // RuntimeException 재throw → DefaultErrorHandler 가 재시도 3회 후 DLQ 이동
        }
    }
    // ── 재고 복구 결과 수신 ────────────────────────────────────────
    @KafkaListener(
            topics = KafkaTopics.STOCK_RESTORE_RESULT,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStockRestoreResult(String message) {
        try {
            StockRestoreResultMessage result =
                    objectMapper.readValue(message, StockRestoreResultMessage.class);

            log.info("[Kafka][CONSUME] stock.restore.result - orderId={}, success={}",
                    result.getOrderId(), result.isSuccess());

            if (result.isSuccess()) {
                // 재고 복구 완료 → 로그만 기록
                log.info("[재고 복구 완료] orderId={}", result.getOrderId());

            } else {
                // 재고 복구 실패 → DLQ 저장 (수동 처리 필요)
                log.error("[재고 복구 실패] orderId={}, reason={}, productId={}",
                        result.getOrderId(), result.getReason(), result.getProductId());

                DlqEventRecord record = DlqEventRecord.create(
                        KafkaTopics.STOCK_RESTORE_RESULT,
                        KafkaTopics.STOCK_RESTORE_RESULT + ".FAILED",
                        result.getOrderId().toString(),
                        message,
                        "재고 복구 실패: " + result.getReason()
                );
                dlqEventJpaRepository.save(record);
            }

        } catch (Exception e) {
            log.error("[Kafka][CONSUME] stock.restore.result 처리 실패 - message={}", message, e);
            throw new RuntimeException("stock.restore.result 처리 실패", e);
        }
    }

    // ── payment.result DLT 수신 ────────────────────────────────────
    @KafkaListener(
            topics = "payment.result.DLT",
            groupId = "order-service-dlq-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentResultDlt(
            String message,
            @Header(KafkaHeaders.DLT_EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.DLT_ORIGINAL_TOPIC) String originalTopic,
            @Header(value = KafkaHeaders.DLT_KEY_EXCEPTION_MESSAGE, required = false) String key) {

        log.error("[DLT] 처리 실패 메시지 수신 - topic={}, error={}",
                originalTopic, exceptionMessage);

        DlqEventRecord record = DlqEventRecord.create(
                originalTopic,
                "payment.result.DLT",
                key,
                message,
                exceptionMessage
        );
        dlqEventJpaRepository.save(record);
    }
}
