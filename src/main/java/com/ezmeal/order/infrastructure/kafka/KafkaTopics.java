package com.ezmeal.order.infrastructure.kafka;

public final class KafkaTopics {

    // ── Order Service → 외부 발행 ───────────────────────────────────
    /** payment-service 수신: 결제 요청 */
    public static final String ORDER_CREATED          = "order.created";

    /** shipment-service 수신: 배달 요청 */
    public static final String SHIPMENT_REQUESTED     = "shipment.requested";

    /** payment-service + shipment-service 수신: 주문 취소 */
    public static final String ORDER_CANCELLED        = "order.cancelled";

    /** notification-service 수신: 모든 상태 변경 알림 */
    public static final String ORDER_STATUS_CHANGED   = "order.status.changed";

    /** notification-service 수신: 배달 완료 → 리뷰 요청 알림 */
    public static final String ORDER_COMPLETED        = "order.completed";

    // ── 외부 → Order Service 수신 ───────────────────────────────────
    /** payment-service 발행: 결제 처리 결과 (성공/실패) */
    public static final String PAYMENT_RESULT         = "payment.result";

    private KafkaTopics() {}
}
