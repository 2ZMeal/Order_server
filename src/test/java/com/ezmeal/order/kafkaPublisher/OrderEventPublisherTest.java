package com.ezmeal.order.kafkaPublisher;

import com.ezmeal.order.domain.event.*;
import com.ezmeal.order.infrastructure.kafka.KafkaTopics;
import com.ezmeal.order.infrastructure.kafka.OrderEventPublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventPublisher 테스트")
class OrderEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderEventPublisher publisher;

    private UUID orderId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        // KafkaTemplate.send()가 CompletableFuture를 반환하도록 mock 설정
        CompletableFuture<SendResult<String, Object>> future = buildMockFuture();
        given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(future);
    }

    @Test
    @DisplayName("publishOrderCreated: order.created 토픽에 올바른 key로 발행")
    void publishOrderCreated_SendsToCorrectTopic() {
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(orderId)
                .companyId(companyId)
                .userName("test_user")
                .totalPrice(25000)
                .items(List.of())
                .occurredAt(LocalDateTime.now())
                .build();

        publisher.publishOrderCreated(event);

        then(kafkaTemplate).should().send(
                eq(KafkaTopics.ORDER_CREATED),
                eq(orderId.toString()),
                eq(event));
    }

    @Test
    @DisplayName("publishShipmentRequested: shipment.requested 토픽에 발행")
    void publishShipmentRequested_SendsToCorrectTopic() {
        ShipmentRequestedEvent event = ShipmentRequestedEvent.builder()
                .orderId(orderId)
                .companyId(companyId)
                .userName("test_user")
                .deliveryAddress("서울시 강남구")
                .occurredAt(LocalDateTime.now())
                .build();

        publisher.publishShipmentRequested(event);

        then(kafkaTemplate).should().send(
                eq(KafkaTopics.SHIPMENT_REQUESTED),
                eq(orderId.toString()),
                eq(event));
    }

    @Test
    @DisplayName("publishOrderCancelled: order.cancelled 토픽에 발행")
    void publishOrderCancelled_SendsToCorrectTopic() {
        OrderCancelledEvent event = OrderCancelledEvent.builder()
                .orderId(orderId)
                .cancelledBy("test_user")
                .requiresPaymentCancellation(true)
                .requiresShipmentCancellation(false)
                .occurredAt(LocalDateTime.now())
                .build();

        publisher.publishOrderCancelled(event);

        then(kafkaTemplate).should().send(
                eq(KafkaTopics.ORDER_CANCELLED),
                eq(orderId.toString()),
                eq(event));
    }

    @Test
    @DisplayName("publishOrderStatusChanged: order.status.changed 토픽에 발행")
    void publishOrderStatusChanged_SendsToCorrectTopic() {
        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
                .orderId(orderId)
                .userName("test_user")
                .previousStatus("PENDING")
                .currentStatus("CONFIRMED")
                .occurredAt(LocalDateTime.now())
                .build();

        publisher.publishOrderStatusChanged(event);

        then(kafkaTemplate).should().send(
                eq(KafkaTopics.ORDER_STATUS_CHANGED),
                eq(orderId.toString()),
                eq(event));
    }

    @Test
    @DisplayName("publishOrderCompleted: order.completed 토픽에 발행")
    void publishOrderCompleted_SendsToCorrectTopic() {
        OrderCompletedEvent event = OrderCompletedEvent.builder()
                .orderId(orderId)
                .userName("test_user")
                .productNames(List.of("도시락A", "도시락B"))
                .completedAt(LocalDateTime.now())
                .build();

        publisher.publishOrderCompleted(event);

        then(kafkaTemplate).should().send(
                eq(KafkaTopics.ORDER_COMPLETED),
                eq(orderId.toString()),
                eq(event));
    }

    // ================================================================
    // 헬퍼
    // ================================================================

    private CompletableFuture<SendResult<String, Object>> buildMockFuture() {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("test-topic", 0), 0L, 0, 0L, 0, 0);
        ProducerRecord<String, Object> record = new ProducerRecord<>("test-topic", "key", new Object());
        SendResult<String, Object> sendResult = new SendResult<>(record, metadata);
        return CompletableFuture.completedFuture(sendResult);
    }
}
