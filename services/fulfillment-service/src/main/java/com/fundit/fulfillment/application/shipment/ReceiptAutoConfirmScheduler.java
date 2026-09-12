package com.fundit.fulfillment.application.shipment;

import com.fundit.fulfillment.domain.shipment.Shipment;
import com.fundit.fulfillment.domain.shipment.ShipmentRepository;
import com.fundit.fulfillment.domain.shipment.ShipmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * FULFILLMENT-010 — {@code status='DELIVERED'}이고 {@code delivered_at}이 N일[정책값, 초안 7일]
 * 이상 경과했는데 구매자가 수령확인을 하지 않은 건을 자동으로 {@code RECEIPT_CONFIRMED}
 * (receipt_auto_confirmed=true)로 확정 처리한다.
 */
@Component
public class ReceiptAutoConfirmScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReceiptAutoConfirmScheduler.class);

    private final ShipmentRepository shipmentRepository;
    private final ReceiptAutoConfirmProcessor processor;
    private final long autoConfirmThresholdDays;

    public ReceiptAutoConfirmScheduler(ShipmentRepository shipmentRepository,
                                        ReceiptAutoConfirmProcessor processor,
                                        @Value("${fulfillment.auto-confirm-threshold-days:7}") long autoConfirmThresholdDays) {
        this.shipmentRepository = shipmentRepository;
        this.processor = processor;
        this.autoConfirmThresholdDays = autoConfirmThresholdDays;
    }

    @Scheduled(fixedDelayString = "${fulfillment.receipt-auto-confirm.interval-ms:3600000}")
    public void run() {
        Instant threshold = Instant.now().minus(autoConfirmThresholdDays, ChronoUnit.DAYS);
        List<Shipment> targets = shipmentRepository.findByStatusAndDeliveredAtBefore(ShipmentStatus.DELIVERED, threshold);
        int processed = 0;
        for (Shipment shipment : targets) {
            try {
                if (processor.autoConfirmOne(shipment.getFundingId())) {
                    processed++;
                }
            } catch (RuntimeException e) {
                log.error("미확인 배송 자동확정 처리 실패 — 다음 배치에서 재시도. fundingId={}", shipment.getFundingId(), e);
            }
        }
        if (processed > 0) {
            log.info("미확인 배송 자동확정 {}건을 처리했습니다.", processed);
        }
    }
}
