package com.fundit.fulfillment.application.tracker;

import com.fundit.common.error.BusinessException;
import com.fundit.common.error.CommonErrorCode;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ScheduleChangedEvent;
import com.fundit.fulfillment.application.project.ProjectOwnershipClient;
import com.fundit.fulfillment.domain.schedulechange.ScheduleChangeReasonType;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.schedulechange.FulfillmentScheduleChangeJpaRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** FULFILLMENT-005 — 판매자의 일정 변경·지연사유 등록(API #4). */
@Service
@RequiredArgsConstructor
public class ScheduleChangeService {

    private final FulfillmentTrackerRepository trackerRepository;
    private final FulfillmentStageDetailJpaRepository stageDetailJpaRepository;
    private final FulfillmentScheduleChangeJpaRepository scheduleChangeJpaRepository;
    private final ProjectOwnershipClient projectOwnershipClient;
    private final FulfillmentNotificationPublisher notificationPublisher;

    @Transactional
    public FulfillmentScheduleChangeJpaEntity registerScheduleChange(Long projectId, UUID sellerId,
                                                                      FulfillmentStage stage,
                                                                      ScheduleChangeReasonType reasonType,
                                                                      String reasonDetail, Instant newPlannedDate) {
        verifyOwnership(projectId, sellerId);
        if (reasonType == ScheduleChangeReasonType.OTHER && (reasonDetail == null || reasonDetail.isBlank())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "OTHER 사유는 상세 사유(reasonDetail)가 필요합니다.");
        }

        FulfillmentTracker tracker = trackerRepository.findByProjectId(projectId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND,
                        "제작·배송 트래커가 없습니다. 아직 펀딩이 성립되지 않은 프로젝트일 수 있습니다."));

        Optional<FulfillmentStageDetailJpaEntity> latest =
                stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(tracker.getId(), stage.name());
        Instant oldPlannedDate = latest.map(FulfillmentStageDetailJpaEntity::getPlannedEndAt).orElse(null);

        FulfillmentScheduleChangeJpaEntity change = scheduleChangeJpaRepository.save(
                FulfillmentScheduleChangeJpaEntity.builder()
                        .trackerId(tracker.getId())
                        .stage(stage.name())
                        .reasonType(reasonType.name())
                        .reasonDetail(reasonDetail)
                        .oldPlannedDate(oldPlannedDate)
                        .newPlannedDate(newPlannedDate)
                        .build());

        // 해당 단계의 예상일정을 새 값으로 갱신한다 — append-only 원칙(FULFILLMENT-002)에 따라
        // 수정이 아니라 새 상세내용 행을 추가하고, updated_at DESC 1건 조회가 항상 최신값을 보게 한다.
        stageDetailJpaRepository.save(FulfillmentStageDetailJpaEntity.builder()
                .trackerId(tracker.getId())
                .stage(stage.name())
                .plannedStartAt(latest.map(FulfillmentStageDetailJpaEntity::getPlannedStartAt).orElse(null))
                .plannedEndAt(newPlannedDate)
                .detailText(latest.map(FulfillmentStageDetailJpaEntity::getDetailText)
                        .orElse("[일정 변경] " + reasonType.name() + (reasonDetail != null ? ": " + reasonDetail : "")))
                .build());

        notificationPublisher.publishScheduleChanged(new ScheduleChangedEvent(projectId, stage, reasonType, newPlannedDate));

        return change;
    }

    private void verifyOwnership(Long projectId, UUID sellerId) {
        UUID actualSellerId = projectOwnershipClient.getSellerId(projectId);
        if (!actualSellerId.equals(sellerId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}
