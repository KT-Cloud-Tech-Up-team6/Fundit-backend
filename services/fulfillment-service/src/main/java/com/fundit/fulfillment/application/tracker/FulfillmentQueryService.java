package com.fundit.fulfillment.application.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** FULFILLMENT-003 — 프로젝트 단위 제작·배송 진행 현황 조회(API #1, 공통·인증 불필요). */
@Service
public class FulfillmentQueryService {

    private final FulfillmentTrackerRepository trackerRepository;
    private final FulfillmentStageDetailJpaRepository stageDetailJpaRepository;
    private final FulfillmentScheduleChangeJpaRepository scheduleChangeJpaRepository;
    private final long staleUpdateThresholdDays;

    public FulfillmentQueryService(FulfillmentTrackerRepository trackerRepository,
                                    FulfillmentStageDetailJpaRepository stageDetailJpaRepository,
                                    FulfillmentScheduleChangeJpaRepository scheduleChangeJpaRepository,
                                    @Value("${fulfillment.stale-update-threshold-days:7}") long staleUpdateThresholdDays) {
        this.trackerRepository = trackerRepository;
        this.stageDetailJpaRepository = stageDetailJpaRepository;
        this.scheduleChangeJpaRepository = scheduleChangeJpaRepository;
        this.staleUpdateThresholdDays = staleUpdateThresholdDays;
    }

    @Transactional(readOnly = true)
    public ProjectFulfillmentView getProjectFulfillment(Long projectId) {
        FulfillmentTracker tracker = trackerRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "제작·배송 트래커가 없습니다. 아직 펀딩이 성립되지 않은 프로젝트일 수 있습니다."));

        List<StageSnapshot> stages = List.of(FulfillmentStage.values()).stream()
                .map(stage -> toStageSnapshot(tracker, stage))
                .toList();

        List<FulfillmentScheduleChangeJpaEntity> scheduleChanges =
                scheduleChangeJpaRepository.findByTrackerIdOrderByChangedAtDesc(tracker.getId());

        return new ProjectFulfillmentView(tracker.getProjectId(), tracker.getCurrentStage(),
                tracker.getLastUpdatedAt(), isOverdue(tracker), stages, scheduleChanges);
    }

    private StageSnapshot toStageSnapshot(FulfillmentTracker tracker, FulfillmentStage stage) {
        StageProgressStatus status;
        if (stage.ordinal() < tracker.getCurrentStage().ordinal()) {
            status = StageProgressStatus.COMPLETED;
        } else if (stage == tracker.getCurrentStage()) {
            status = StageProgressStatus.IN_PROGRESS;
        } else {
            status = StageProgressStatus.NOT_STARTED;
        }

        Optional<FulfillmentStageDetailJpaEntity> latest =
                stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(tracker.getId(), stage.name());
        return new StageSnapshot(stage, status,
                latest.map(FulfillmentStageDetailJpaEntity::getPlannedStartAt).orElse(null),
                latest.map(FulfillmentStageDetailJpaEntity::getPlannedEndAt).orElse(null),
                latest.map(FulfillmentStageDetailJpaEntity::getDetailText).orElse(null),
                latest.map(FulfillmentStageDetailJpaEntity::getUpdatedAt).orElse(null));
    }

    /** FULFILLMENT-003/004 — DELIVERY 단계는 이미 전원 발송된 상태라 갱신 알림 대상에서 제외한다. */
    private boolean isOverdue(FulfillmentTracker tracker) {
        if (tracker.getCurrentStage() == FulfillmentStage.DELIVERY) {
            return false;
        }
        Instant basis = tracker.getLastUpdatedAt() != null ? tracker.getLastUpdatedAt() : tracker.getCreatedAt();
        return basis != null && basis.isBefore(Instant.now().minus(staleUpdateThresholdDays, ChronoUnit.DAYS));
    }

    public enum StageProgressStatus {
        COMPLETED, IN_PROGRESS, NOT_STARTED
    }

    public record StageSnapshot(FulfillmentStage stage, StageProgressStatus status, Instant plannedStartAt,
                                 Instant plannedEndAt, String detailText, Instant updatedAt) {
    }

    public record ProjectFulfillmentView(Long projectId, FulfillmentStage currentStage, Instant lastUpdatedAt,
                                          boolean updateOverdue, List<StageSnapshot> stages,
                                          List<FulfillmentScheduleChangeJpaEntity> scheduleChanges) {
    }
}
