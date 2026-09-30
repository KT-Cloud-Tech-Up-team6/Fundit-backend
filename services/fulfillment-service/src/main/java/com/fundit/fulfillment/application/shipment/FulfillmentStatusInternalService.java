package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTracker;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FULFILLMENT-008 — payment-service 연동 내부 API(API #8). shipments 레코드가 있으면 그 값을
 * 그대로 쓰고, 없으면(아직 발송 전) order-service에서 projectId를 조회해 SHIPPING_OUT 단계의
 * 발송 예정일과 현재 시각을 비교해 지연 여부를 판정한다.
 */
@Service
@RequiredArgsConstructor
public class FulfillmentStatusInternalService {

    private final ShipmentRepository shipmentRepository;
    private final OrderFundingClient orderFundingClient;
    private final FulfillmentTrackerRepository trackerRepository;
    private final FulfillmentStageDetailJpaRepository stageDetailJpaRepository;

    @Transactional(readOnly = true)
    public FulfillmentStatusView getStatus(UUID fundingId) {
        Optional<Shipment> shipment = shipmentRepository.findByFundingId(fundingId);
        if (shipment.isPresent()) {
            Shipment s = shipment.get();
            boolean alreadyShipped = s.getStatus() != ShipmentStatus.PREPARING;
            // 이미 발송된 건은 "발송 지연" 개념 자체가 더 이상 의미가 없어 isDelayed=false로 고정한다.
            // 진행 기록은 프로젝트 단위라 shipments 행이 들고 있는 projectId로 트래커를 찾는다
            // (order-service 재조회 없음).
            return new FulfillmentStatusView(alreadyShipped, false, s.getDeliveredAt(), s.getReceiptConfirmedAt(),
                    hasProgressRecord(trackerRepository.findByProjectId(s.getProjectId())));
        }

        FundingSnapshot snapshot = orderFundingClient.fetch(fundingId);
        Optional<FulfillmentTracker> tracker = trackerRepository.findByProjectId(snapshot.projectId());
        boolean delayed = tracker
                .flatMap(t -> stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(
                        t.getId(), FulfillmentStage.SHIPPING_OUT.name()))
                .map(FulfillmentStageDetailJpaEntity::getPlannedEndAt)
                .map(plannedEndAt -> Instant.now().isAfter(plannedEndAt))
                .orElse(false);

        return new FulfillmentStatusView(false, delayed, null, null, hasProgressRecord(tracker));
    }

    /**
     * "진행 기록 있음" 판정 — 트래커의 {@code lastUpdatedAt}이 채워졌는지만 본다. 판매자가 상세
     * 진행 내용을 등록할 때만({@code StageProgressService.registerStageDetail}) 채워지므로,
     * 일정 변경만 등록한 경우나 단계 전환만 한 경우는 기록으로 보지 않는다(PM 결정 09-29 —
     * "1주 미갱신 알림"(FULFILLMENT-004) 기준과 같다).
     */
    private boolean hasProgressRecord(Optional<FulfillmentTracker> tracker) {
        return tracker.map(t -> t.getLastUpdatedAt() != null).orElse(false);
    }

    /** v1 payment — 레거시 Long PK를 order-service에서 orderId(UUID)로 해석한 뒤 UUID 경로를 탄다. */
    @Transactional(readOnly = true)
    public FulfillmentStatusView getStatus(Long fundingId) {
        return getStatus(orderFundingClient.fetchByInternalId(fundingId).fundingPublicId());
    }

    /**
     * order-service 주문 목록(V03/V06)용 배치 조회. 단건 API와 달리 아직 발송 전(shipments 행 없음)
     * 건의 발송지연 여부는 계산하지 않는다 — 목록 배지/가능액션 판단에는 필요 없는 값이라
     * order-service별 project-service 조회까지 얹지 않기 위한 의도적 단순화다.
     * ponytail: 발송 전 건의 지연 여부가 배치 응답에도 필요해지면, order-service에도
     * projectId 배치 조회를 추가해 이 메서드에서 트래커 단계를 함께 판정할 것.
     */
    @Transactional(readOnly = true)
    public List<FulfillmentBatchStatusView> getStatuses(List<UUID> fundingIds) {
        if (fundingIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Shipment> shipmentsByFundingId = shipmentRepository.findByFundingIdIn(fundingIds).stream()
                .collect(Collectors.toMap(Shipment::getFundingId, Function.identity()));
        return fundingIds.stream()
                .map(fundingId -> {
                    Shipment shipment = shipmentsByFundingId.get(fundingId);
                    boolean alreadyShipped = shipment != null && shipment.getStatus() != ShipmentStatus.PREPARING;
                    Instant deliveredAt = shipment != null ? shipment.getDeliveredAt() : null;
                    return new FulfillmentBatchStatusView(fundingId, alreadyShipped, deliveredAt);
                })
                .toList();
    }

    /**
     * order-service 주문 목록(V03)의 발송지연 배지·가능액션 판정용 — 프로젝트 단위로 묻는다.
     * 지연은 원래 프로젝트 단위 판정(SHIPPING_OUT 단계의 발송 예정일 경과)이고, order-service는
     * 주문마다 projectId를 이미 들고 있다. 펀딩 단위로 받으면 이쪽이 order-service에 projectId를
     * 다시 배치로 물어봐야 해(order → fulfillment → order) 왕복이 한 번 더 생긴다.
     *
     * <p>"미발송"까지 합쳐 판정하는 것은 호출부 몫이다 — 발송 여부는 펀딩 단위(shipments)라
     * 배치 상태 조회({@link #getStatuses})가 이미 내려주고 있다.
     */
    @Transactional(readOnly = true)
    public List<ProjectShippingDelayView> getShippingDelays(List<UUID> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        List<FulfillmentTracker> trackers = trackerRepository.findByProjectIdIn(projectIds);
        Map<Long, UUID> projectIdByTrackerId = trackers.stream()
                .collect(Collectors.toMap(FulfillmentTracker::getId, FulfillmentTracker::getProjectId));
        if (projectIdByTrackerId.isEmpty()) {
            return notDelayed(projectIds);
        }

        Instant now = Instant.now();
        Map<UUID, Boolean> delayedByProjectId = new LinkedHashMap<>();
        // 정렬이 updatedAt 내림차순이라 트래커별 첫 행이 최신 상세다(putIfAbsent로 그 첫 행만 쓴다).
        for (FulfillmentStageDetailJpaEntity detail : stageDetailJpaRepository
                .findByTrackerIdInAndStageOrderByUpdatedAtDesc(List.copyOf(projectIdByTrackerId.keySet()),
                        FulfillmentStage.SHIPPING_OUT.name())) {
            UUID projectId = projectIdByTrackerId.get(detail.getTrackerId());
            delayedByProjectId.putIfAbsent(projectId,
                    detail.getPlannedEndAt() != null && now.isAfter(detail.getPlannedEndAt()));
        }

        Map<UUID, Boolean> hasRecordByProjectId = trackers.stream()
                .collect(Collectors.toMap(FulfillmentTracker::getProjectId, t -> t.getLastUpdatedAt() != null));

        return projectIds.stream()
                .map(projectId -> new ProjectShippingDelayView(projectId,
                        delayedByProjectId.getOrDefault(projectId, false),
                        hasRecordByProjectId.getOrDefault(projectId, false)))
                .toList();
    }

    private List<ProjectShippingDelayView> notDelayed(List<UUID> projectIds) {
        return projectIds.stream().map(projectId -> new ProjectShippingDelayView(projectId, false, false)).toList();
    }

    public record FulfillmentStatusView(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt,
                                         Instant receiptConfirmedAt, boolean hasProgressRecord) {
    }

    /** 발송 예정일이 없거나 트래커가 없는 프로젝트는 {@code isDelayed=false}다(지연으로 단정하지 않는다). */
    public record ProjectShippingDelayView(UUID projectId, boolean isDelayed, boolean hasProgressRecord) {
    }

    public record FulfillmentBatchStatusView(UUID fundingId, boolean isAlreadyShipped, Instant deliveredAt) {
    }
}
