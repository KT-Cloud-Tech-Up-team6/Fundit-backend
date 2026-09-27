package com.fundit.fulfillment.domain.tracker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FulfillmentTrackerRepository {

    boolean existsByProjectId(UUID projectId);

    FulfillmentTracker save(FulfillmentTracker tracker);

    Optional<FulfillmentTracker> findByProjectId(UUID projectId);

    /** order-service 주문 목록(V03)의 발송지연 판정용 배치 조회 — 없는 프로젝트는 결과에서 빠진다. */
    List<FulfillmentTracker> findByProjectIdIn(List<UUID> projectIds);

    /**
     * FULFILLMENT-004 배치 대상 조회 — DELIVERY가 아니면서 threshold 이전에 마지막으로 갱신된
     * (한 번도 갱신되지 않은 경우 포함) 트래커. idx_fulfillment_trackers_stale 활용.
     */
    List<FulfillmentTracker> findStale(Instant threshold);
}
