package com.fundit.fulfillment.domain.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — 5단계 순방향 전용 상태 전이 불변식이 있다.
 * 프로젝트당 1행이며 참여자 전원이 공유한다(생산 공정은 구매자마다 다르지 않음).
 */
@Getter
@Builder(toBuilder = true)
public class FulfillmentTracker {

    private final Long id;
    private final Long projectId;
    private FulfillmentStage currentStage;
    private Instant lastUpdatedAt;
    private final Instant createdAt;

    /** FULFILLMENT-001 — FundingSucceeded 이벤트 구독 시 생성되는 유일한 경로. */
    public static FulfillmentTracker create(Long projectId) {
        return FulfillmentTracker.builder()
                .projectId(projectId)
                .currentStage(FulfillmentStage.PRODUCTION_START)
                .build();
    }

    /**
     * FULFILLMENT-002 — 단계 전환. 순방향 전이만 허용하며, 현재보다 앞선(지난) 단계로
     * 되돌리려 하면 예외를 던진다. 같은 단계로의 요청은 idempotent하게 무시한다.
     */
    public void advanceTo(FulfillmentStage target) {
        if (target.isBefore(currentStage)) {
            throw new BusinessException(FulfillmentErrorCode.INVALID_STAGE_TRANSITION);
        }
        this.currentStage = target;
    }

    /** FULFILLMENT-002 — 상세 진행 내용 등록 시 1주 미갱신 알림(FULFILLMENT-004)의 기준 시각을 리셋한다. */
    public void markProgressUpdated(Instant at) {
        this.lastUpdatedAt = at;
    }
}
