package com.fundit.fulfillment.infrastructure.persistence.stagedetail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FulfillmentStageDetailJpaRepository extends JpaRepository<FulfillmentStageDetailJpaEntity, Long> {

    /** FULFILLMENT-003 — 단계별 "최신 상세내용 1건" 조회(idx_fulfillment_stage_details_tracker 활용). */
    Optional<FulfillmentStageDetailJpaEntity> findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(Long trackerId, String stage);

    List<FulfillmentStageDetailJpaEntity> findByTrackerIdOrderByUpdatedAtDesc(Long trackerId);

    /** 위 단건 조회의 배치판(발송지연 판정용) — 트래커별 최신 1건 선택은 호출부가 한다. */
    List<FulfillmentStageDetailJpaEntity> findByTrackerIdInAndStageOrderByUpdatedAtDesc(List<Long> trackerIds,
                                                                                        String stage);
}
