package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher;
import com.fundit.fulfillment.application.notification.FulfillmentNotificationPublisher.ReceiptAutoConfirmedEvent;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * FULFILLMENT-010 — 미확인 배송 자동 확정 한 건. 상태 전이와 안내 알림 발행을 한 트랜잭션으로
 * 묶어, 배치 대상 건마다 독립적으로 성공/실패하게 한다(order-service
 * {@code PaymentExpirationProcessor}와 동일한 방어 스타일).
 */
@Service
@RequiredArgsConstructor
public class ReceiptAutoConfirmProcessor {

    private final ShipmentRepository shipmentRepository;
    private final FulfillmentNotificationPublisher notificationPublisher;

    /** @return true면 이번 호출로 자동확정 처리됨, false면 이미 처리돼 있었음(idempotent). */
    @Transactional
    public boolean autoConfirmOne(Long fundingId) {
        return shipmentRepository.findByFundingId(fundingId)
                .filter(shipment -> shipment.getStatus() == ShipmentStatus.DELIVERED)
                .map(shipment -> {
                    shipment.confirmReceipt(Instant.now(), true);
                    shipmentRepository.save(shipment);
                    notificationPublisher.publishReceiptAutoConfirmed(new ReceiptAutoConfirmedEvent(fundingId));
                    return true;
                })
                .orElse(false);
    }
}
