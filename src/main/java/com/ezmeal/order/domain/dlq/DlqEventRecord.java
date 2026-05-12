package com.ezmeal.order.domain.dlq;

import com.ezmeal.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "p_dlq_event_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DlqEventRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_topic", nullable = false, length = 100)
    private String originalTopic;   // 원래 토픽 (예: payment.result)

    @Column(name = "dlt_topic", nullable = false, length = 100)
    private String dltTopic;        // DLT 토픽 또는 실패 구분값

    @Column(name = "message_key", length = 255)
    private String messageKey;      // 메시지 키 (보통 orderId)

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;         // 실패한 원본 메시지 내용

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;    // 실패 원인

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DlqStatus status = DlqStatus.UNRESOLVED;

    public enum DlqStatus {
        UNRESOLVED,  // 미처리 (기본값)
        RESOLVED     // 처리 완료 (수동 재처리 또는 폐기)
    }

    public static DlqEventRecord create(String originalTopic, String dltTopic,
                                        String messageKey, String payload,
                                        String errorMessage) {
        return DlqEventRecord.builder()
                .originalTopic(originalTopic)
                .dltTopic(dltTopic)
                .messageKey(messageKey)
                .payload(payload)
                .errorMessage(errorMessage)
                .build();
    }

    public void markResolved() {
        this.status = DlqStatus.RESOLVED;
    }
}
