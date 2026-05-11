package com.ezmeal.order.infrastructure.kafka;

import com.ezmeal.common.message.EventEnvelope;
import com.ezmeal.common.message.inbox.InboxProcessor;
import com.ezmeal.common.security.principal.CustomUserPrincipal;
import com.ezmeal.common.enums.Role;
import com.ezmeal.order.application.saga.OrderSagaOrchestrator;
import com.ezmeal.order.domain.dlq.DlqEventRecord;
import com.ezmeal.order.infrastructure.kafka.dto.PaymentResultMessage;
import com.ezmeal.order.infrastructure.kafka.dto.StockRestoreResultMessage;
import com.ezmeal.order.infrastructure.persistence.DlqEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final InboxProcessor inboxProcessor;            // 공통 모듈 주입
    private final DlqEventJpaRepository dlqEventJpaRepository;

    // ── 결제 결과 수신 ─────────────────────────────────────────────
    /**
     * payment-service 로부터 결제 결과 수신
     *
     * 공통 모듈 KafkaConsumerConfig 의 StringJsonMessageConverter 가
     * JSON 문자열을 EventEnvelope<PaymentResultMessage> 로 자동 역직렬화
     *
     * InboxProcessor.processOnce() 로 중복 수신 방어
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_RESULT,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumePaymentResult(EventEnvelope<PaymentResultMessage> envelope) {

        // processOnce(): 같은 eventId 가 오면 람다식 안 실행 (멱등성 보장)
        inboxProcessor.processOnce(envelope.eventId(), () -> {

            PaymentResultMessage result = envelope.payload();
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
        });
    }



    // ── 재고 복구 결과 수신 ────────────────────────────────────────
    /**
     * product-service 가 재고 복구 후 발행하는 결과 이벤트 수신
     *
     * 성공: 로그 기록
     * 실패: DLQ 저장 → 수동 처리 필요
     */
    @KafkaListener(
            topics = KafkaTopics.STOCK_RESTORE_RESULT,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeStockRestoreResult(EventEnvelope<StockRestoreResultMessage> envelope) {

        inboxProcessor.processOnce(envelope.eventId(), () -> {

            StockRestoreResultMessage result = envelope.payload();

            log.info("[Kafka][CONSUME] stock.restore.result - orderId={}, success={}",
                    result.getOrderId(), result.isSuccess());

            if (result.isSuccess()) {
                log.info("[재고 복구 완료] orderId={}", result.getOrderId());

            } else {
                log.error("[재고 복구 실패] orderId={}, reason={}, productId={}",
                        result.getOrderId(), result.getReason(), result.getProductId());

                dlqEventJpaRepository.save(DlqEventRecord.create(
                        KafkaTopics.STOCK_RESTORE_RESULT,
                        KafkaTopics.STOCK_RESTORE_RESULT + ".FAILED",
                        result.getOrderId().toString(),
                        result.toString(),
                        "재고 복구 실패: " + result.getReason()
                ));
            }
        });
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

    // ── 현재 인증 정보 추출 헬퍼 ──────────────────────────────────
    // KafkaSecurityInterceptor 가 헤더에서 복구해준 유저 정보를 꺼냄
    private CustomUserPrincipal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CustomUserPrincipal principal) {
            return principal;
        }
        return new CustomUserPrincipal("SYSTEM", Role.ADMIN, "system@ezmeal.com");
    }

}
