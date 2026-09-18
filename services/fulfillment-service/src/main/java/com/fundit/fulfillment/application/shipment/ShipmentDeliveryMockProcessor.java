package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.funding.FulfillmentDomainEventPublisher;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * FULFILLMENT-007 — 배송완료 목업 처리 한 건. 배치 대상 건마다 별도 트랜잭션으로 처리해, 한 건
 * 실패가 이미 처리된 다른 건까지 함께 롤백시키지 않게 한다(order-service
 * {@code PaymentExpirationProcessor}와 동일한 방어 스타일).
 */
@Service
@RequiredArgsConstructor
public class ShipmentDeliveryMockProcessor {

    private final ShipmentRepository shipmentRepository;
    private final FulfillmentDomainEventPublisher domainEventPublisher;

    /** @return true면 이번 호출로 배송완료 처리됨, false면 이미 처리돼 있었음(idempotent). */
    @Transactional
    public boolean markDeliveredOne(UUID fundingId) {
        // 배치가 대상 목록을 조회한 시점과 처리 시점 사이에 상태가 바뀌었을 수 있어 매번 최신값을 다시 읽는다.
        return shipmentRepository.findByFundingId(fundingId)
                .filter(shipment -> shipment.getStatus() == ShipmentStatus.SHIPPED)
                .map(shipment -> {
                    shipment.markDelivered(Instant.now());
                    shipmentRepository.save(shipment);
                    // payment-service PAYMENT-014(최종정산) 트리거 — 상태 전이와 같은 트랜잭션에서 적재.
                    domainEventPublisher.publishShippingCompleted(new FulfillmentDomainEventPublisher.ShippingCompletedEvent(
                            shipment.getFundingId(), shipment.getProjectId()));
                    return true;
                })
                .orElse(false);
    }
}
