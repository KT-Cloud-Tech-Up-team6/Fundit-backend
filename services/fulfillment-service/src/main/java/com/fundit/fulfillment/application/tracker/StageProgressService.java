package com.fundit.fulfillment.application.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * FULFILLMENT-002 — 판매자의 단계 전환(API #2) 및 단계별 예상일정·상세 진행 내용 등록(API #3).
 * {@code fulfillment_stage_details}는 단순 애그리거트(persistence-convention.md 2번)라
 * JpaRepository를 직접 다룬다.
 */
@Service
@RequiredArgsConstructor
public class StageProgressService {

    private final FulfillmentTrackerRepository trackerRepository;
    private final FulfillmentStageDetailJpaRepository stageDetailJpaRepository;
    private final ProjectOwnershipClient projectOwnershipClient;

    @Transactional
    public FulfillmentTracker transitionStage(Long projectId, UUID sellerId, FulfillmentStage target) {
        verifyOwnership(projectId, sellerId);
        FulfillmentTracker tracker = getTrackerOrThrow(projectId);
        tracker.advanceTo(target);
        return trackerRepository.save(tracker);
    }

    @Transactional
    public FulfillmentStageDetailJpaEntity registerStageDetail(Long projectId, UUID sellerId, FulfillmentStage stage,
                                                                Instant plannedStartAt, Instant plannedEndAt,
                                                                String detailText) {
        verifyOwnership(projectId, sellerId);
        FulfillmentTracker tracker = getTrackerOrThrow(projectId);

        Instant now = Instant.now();
        FulfillmentStageDetailJpaEntity saved = stageDetailJpaRepository.save(FulfillmentStageDetailJpaEntity.builder()
                .trackerId(tracker.getId())
                .stage(stage.name())
                .plannedStartAt(plannedStartAt)
                .plannedEndAt(plannedEndAt)
                .detailText(detailText)
                .updatedAt(now)
                .build());

        // 1주 강제 정책(FULFILLMENT-004)의 기준 시각을 리셋한다.
        tracker.markProgressUpdated(now);
        trackerRepository.save(tracker);
        return saved;
    }

    private FulfillmentTracker getTrackerOrThrow(Long projectId) {
        return trackerRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "제작·배송 트래커가 없습니다. 아직 펀딩이 성립되지 않은 프로젝트일 수 있습니다."));
    }

    private void verifyOwnership(Long projectId, UUID sellerId) {
        UUID actualSellerId = projectOwnershipClient.getSellerId(projectId);
        if (!actualSellerId.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
