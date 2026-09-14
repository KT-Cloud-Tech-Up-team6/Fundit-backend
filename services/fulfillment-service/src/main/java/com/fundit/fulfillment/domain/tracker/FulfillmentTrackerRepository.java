package com.fundit.fulfillment.domain.tracker;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FulfillmentTrackerRepository {

    boolean existsByProjectId(Long projectId);

    FulfillmentTracker save(FulfillmentTracker tracker);

    Optional<FulfillmentTracker> findByProjectId(Long projectId);

    /**
     * FULFILLMENT-004 배치 대상 조회 — DELIVERY가 아니면서 threshold 이전에 마지막으로 갱신된
     * (한 번도 갱신되지 않은 경우 포함) 트래커. idx_fulfillment_trackers_stale 활용.
     */
    List<FulfillmentTracker> findStale(Instant threshold);
}
