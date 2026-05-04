package com.ezmeal.order.domain.entity;

import com.ezmeal.common.entity.BaseEntity;
import com.ezmeal.common.exception.CustomException;
import com.ezmeal.order.domain.exception.OrderErrorCode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "p_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at IS NULL")
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;            // customerUsername → customerId

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "delivery_address", length = 255)
    private String deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Column(name = "request_note", columnDefinition = "TEXT")
    private String requestNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "saga_status", length = 30)
    private SagaStatus sagaStatus;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();



    // ========================
    // Enum 정의
    // ========================

    public enum OrderStatus {
        READY,       // 주문 생성됨 (결제 전)
        PENDING,     // 결제 진행 중
        CONFIRMED,   // 결제 완료 → 배달 요청 트리거
        DELIVERING,  // 배달 중 (취소 불가)
        COMPLETED,   // 배달 완료 → 리뷰 요청 트리거
        CANCELLED    // 취소됨
    }

    public enum OrderType {
        ONLINE, OFFLINE
    }

    public enum SagaStatus {
        ORDER_CREATED,       // 주문 생성, 결제 요청 이벤트 발행됨
        PAYMENT_REQUESTED,   // 결제 요청 중
        PAYMENT_COMPLETED,   // 결제 완료, 배달 요청 이벤트 발행됨
        PAYMENT_FAILED,      // 결제 실패 (보상 완료)
        SAGA_COMPLETED,      // 전체 SAGA 완료
        SAGA_COMPENSATED     // 보상 트랜잭션 완료 (취소/실패)
    }

    // ========================
    // 정적 팩토리 메서드
    // ========================

    public static Order create(String userId, UUID companyId,
                               String deliveryAddress, Integer totalPrice,
                               String requestNote, OrderType orderType) {
        return Order.builder()
                .userId(userId)
                .companyId(companyId)
                .deliveryAddress(deliveryAddress)
                .totalPrice(totalPrice)
                .requestNote(requestNote)
                .orderType(orderType)
                .status(OrderStatus.READY)
                .sagaStatus(SagaStatus.ORDER_CREATED)
                .build();
    }

    // ========================
    // SAGA 상태 전이 메서드
    // ========================

    /** 결제 요청 중 상태로 전환 */
    public void markPaymentRequested() {
        this.status = OrderStatus.PENDING;
        this.sagaStatus = SagaStatus.PAYMENT_REQUESTED;
    }

    /** 결제 완료 → 주문 확정 */
    public void markPaymentCompleted() {
        this.status = OrderStatus.CONFIRMED;
        this.sagaStatus = SagaStatus.PAYMENT_COMPLETED;
    }

    /** 결제 실패 → 주문 취소 (보상) */
    public void markPaymentFailed() {
        this.status = OrderStatus.CANCELLED;
        this.sagaStatus = SagaStatus.PAYMENT_FAILED;
        super.deleteBySystem();                 // BaseEntity 위임 (deletedAt/By 자동)
    }

    /** SAGA 전체 완료 */
    public void markSagaCompleted() {
        this.sagaStatus = SagaStatus.SAGA_COMPLETED;
    }

    /** SAGA 보상 완료 */
    public void markSagaCompensated() {
        this.sagaStatus = SagaStatus.SAGA_COMPENSATED;
    }

    // ========================
    // 비즈니스 메서드
    // ========================

    /**
     * 주문 취소
     * - DELIVERING, COMPLETED, CANCELLED 상태는 취소 불가
     * - soft delete 처리
     */
    public void cancel(String cancelledBy) {
        if (this.status == OrderStatus.CANCELLED) {
            throw new CustomException(OrderErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (this.status == OrderStatus.DELIVERING) {
            throw new CustomException(OrderErrorCode.ORDER_CANCEL_DELIVERING);
        }
        if (this.status == OrderStatus.COMPLETED) {
            throw new CustomException(OrderErrorCode.ORDER_CANCEL_COMPLETED);
        }
        this.status = OrderStatus.CANCELLED;
        this.sagaStatus = SagaStatus.SAGA_COMPENSATED;
        super.delete(cancelledBy);   // BaseEntity 위임 (updatedAt/By + deletedAt/By 자동)
    }

    /**
     * 주문 상태 변경 (COMPANY 권한)
     * - 취소/완료 후 변경 불가
     * - 상태 전이 규칙 검증
     */
    public void updateStatus(OrderStatus newStatus) {
        if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.COMPLETED) {
            throw new CustomException(OrderErrorCode.ORDER_STATUS_ALREADY_FINAL);
        }
        validateStatusTransition(this.status, newStatus);
        this.status = newStatus;
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        boolean valid = switch (current) {
            case READY      -> next == OrderStatus.PENDING   || next == OrderStatus.CANCELLED;
            case PENDING    -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED  -> next == OrderStatus.DELIVERING || next == OrderStatus.CANCELLED;
            case DELIVERING -> next == OrderStatus.COMPLETED;
            default -> false;
        };
        if (!valid) {
            throw new CustomException(OrderErrorCode.ORDER_STATUS_INVALID_TRANSITION);
        }
    }

    /** 5분 이내 취소 가능 여부 (READY 또는 PENDING 상태) */
    public boolean isCancellable() {
        boolean cancellableStatus =
                this.status == OrderStatus.READY || this.status == OrderStatus.PENDING;
        long minutesPassed =
                java.time.Duration.between(this.createdAt, LocalDateTime.now()).toMinutes();
        return cancellableStatus && minutesPassed <= 5;
    }

    /** 취소 시 배달 서비스에 취소 요청이 필요한지 여부 */
    public boolean requiresShipmentCancellation() {
        // CONFIRMED 상태일 때만 배달 요청이 이미 간 상태 → 취소 요청 필요
        return this.status == OrderStatus.CONFIRMED;
    }

    /** 취소 시 결제 서비스에 취소 요청이 필요한지 여부 */
    public boolean requiresPaymentCancellation() {
        // PENDING, CONFIRMED 상태에서는 결제가 진행됐거나 완료됐으므로 취소 필요
        return this.status == OrderStatus.PENDING || this.status == OrderStatus.CONFIRMED;
    }
}
