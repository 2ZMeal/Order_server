package com.ezmeal.order.infrastructure.persistence;


import com.ezmeal.order.domain.dlq.DlqEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DlqEventJpaRepository extends JpaRepository<DlqEventRecord, UUID> {

    // 미처리 건만 조회 (관리자 확인용)
    List<DlqEventRecord> findByStatus(DlqEventRecord.DlqStatus status);

    // 특정 토픽의 미처리 건 조회
    List<DlqEventRecord> findByOriginalTopicAndStatus(
            String originalTopic,
            DlqEventRecord.DlqStatus status
    );
}
