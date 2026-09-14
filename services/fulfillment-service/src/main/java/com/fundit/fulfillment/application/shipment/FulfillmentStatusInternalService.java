package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.funding.OrderFundingClient;
import com.fundit.fulfillment.application.funding.OrderFundingClient.FundingSnapshot;
import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import com.fundit.fulfillment.domain.tracker.FulfillmentStage;
import com.fundit.fulfillment.domain.tracker.FulfillmentTrackerRepository;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaEntity;
import com.fundit.fulfillment.infrastructure.persistence.stagedetail.FulfillmentStageDetailJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

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
    public FulfillmentStatusView getStatus(Long fundingId) {
        Optional<Shipment> shipment = shipmentRepository.findByFundingId(fundingId);
        if (shipment.isPresent()) {
            Shipment s = shipment.get();
            boolean alreadyShipped = s.getStatus() != ShipmentStatus.PREPARING;
            // 이미 발송된 건은 "발송 지연" 개념 자체가 더 이상 의미가 없어 isDelayed=false로 고정한다.
            return new FulfillmentStatusView(alreadyShipped, false, s.getDeliveredAt(), s.getReceiptConfirmedAt());
        }

        FundingSnapshot snapshot = orderFundingClient.fetch(fundingId);
        boolean delayed = trackerRepository.findByProjectId(snapshot.projectId())
                .flatMap(tracker -> stageDetailJpaRepository.findFirstByTrackerIdAndStageOrderByUpdatedAtDesc(
                        tracker.getId(), FulfillmentStage.SHIPPING_OUT.name()))
                .map(FulfillmentStageDetailJpaEntity::getPlannedEndAt)
                .map(plannedEndAt -> Instant.now().isAfter(plannedEndAt))
                .orElse(false);

        return new FulfillmentStatusView(false, delayed, null, null);
    }

    public record FulfillmentStatusView(boolean isAlreadyShipped, boolean isDelayed, Instant deliveredAt,
                                         Instant receiptConfirmedAt) {
    }
}
