package com.ezmeal.order.infrastructure.kafka;

public final class KafkaTopics {

    // ── Order Service → 외부 발행 ───────────────────────────────────
    /** payment-service 수신: 결제 요청 */
    public static final String ORDER_CREATED          = "order.created";

    /** shipment-service 수신: 배달 요청 */
    public static final String SHIPMENT_REQUESTED     = "order.shipment.requested";

    /** payment-service + shipment-service 수신: 주문 취소 */
    public static final String ORDER_CANCELLED        = "order.cancelled";

    /** notification-service 수신: 모든 상태 변경 알림 */
    public static final String ORDER_STATUS_CHANGED   = "order.status.changed";

    /** notification-service 수신: 배달 완료 → 리뷰 요청 알림 */
    public static final String ORDER_COMPLETED        = "order.completed";

//    // ── 외부 → Order Service 수신 ───────────────────────────────────
//    /** payment-service 발행: 결제 처리 결과 (성공/실패) */
//    public static final String PAYMENT_RESULT         = "payment.result";
    public static final String PAYMENT_COMPLETED    = "payment.completed";
    public static final String PAYMENT_CANCELLED    = "payment.cancelled";
    public static final String PAYMENT_FAILED       = "payment.failed";

//    // 추가: 재고 복구 결과 수신 (product-service 가 발행)
//    public static final String PRODUCT_QUANTITY_RESTORED = "product.quantity.restored";
//  변경: product-service 발행 토픽 (성공/실패 분리)
    public static final String STOCK_RESTORED       = "product.quantity.restored";
    public static final String STOCK_RESTORE_FAILED = "product.quantity.restore.failed";

    private KafkaTopics() {}
}
