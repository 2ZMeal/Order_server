package com.ezmeal.order.domain.entity;

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
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_username", nullable = false, length = 100)
    private String userUsername;

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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 100, nullable = false)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

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

    public static Order create(String userUsername, UUID companyId,
                               String deliveryAddress, Integer totalPrice,
                               String requestNote, OrderType orderType) {
        return Order.builder()
                .userUsername(userUsername)
                .companyId(companyId)
                .deliveryAddress(deliveryAddress)
                .totalPrice(totalPrice)
                .requestNote(requestNote)
                .orderType(orderType)
                .status(OrderStatus.READY)
                .sagaStatus(SagaStatus.ORDER_CREATED)
                .createdAt(LocalDateTime.now())
                .createdBy(userUsername)
                .build();
    }

    // ========================
    // SAGA 상태 전이 메서드
    // ========================

    /** 결제 요청 중 상태로 전환 */
    public void markPaymentRequested() {
        this.status = OrderStatus.PENDING;
        this.sagaStatus = SagaStatus.PAYMENT_REQUESTED;
        this.updatedAt = LocalDateTime.now();
    }

    /** 결제 완료 → 주문 확정 */
    public void markPaymentCompleted(String updatedBy) {
        this.status = OrderStatus.CONFIRMED;
        this.sagaStatus = SagaStatus.PAYMENT_COMPLETED;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
    }

    /** 결제 실패 → 주문 취소 (보상) */
    public void markPaymentFailed(String updatedBy) {
        this.status = OrderStatus.CANCELLED;
        this.sagaStatus = SagaStatus.PAYMENT_FAILED;
        this.updatedBy = updatedBy;
        this.updatedAt = LocalDateTime.now();
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = updatedBy;
    }

    /** SAGA 전체 완료 */
    public void markSagaCompleted() {
        this.sagaStatus = SagaStatus.SAGA_COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /** SAGA 보상 완료 */
    public void markSagaCompensated() {
        this.sagaStatus = SagaStatus.SAGA_COMPENSATED;
        this.updatedAt = LocalDateTime.now();
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
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }
        if (this.status == OrderStatus.DELIVERING) {
            throw new IllegalStateException("배달 중인 주문은 취소할 수 없습니다.");
        }
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("이미 완료된 주문은 취소할 수 없습니다.");
        }

        this.status = OrderStatus.CANCELLED;
        this.sagaStatus = SagaStatus.SAGA_COMPENSATED;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = cancelledBy;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = cancelledBy;
    }

    /**
     * 주문 상태 변경 (COMPANY 권한)
     * - 취소/완료 후 변경 불가
     * - 상태 전이 규칙 검증
     */
    public void updateStatus(OrderStatus newStatus, String updatedBy) {
        if (this.status == OrderStatus.CANCELLED || this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("완료되었거나 취소된 주문은 상태를 변경할 수 없습니다.");
        }
        validateStatusTransition(this.status, newStatus);
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = updatedBy;
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
            throw new IllegalStateException(
                    String.format("상태 변경 불가: [%s] → [%s]", current, next));
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
