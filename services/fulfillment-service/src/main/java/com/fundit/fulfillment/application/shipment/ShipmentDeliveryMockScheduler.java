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
 * FULFILLMENT-007 — 택배사 실연동이 없어(요구사항정의서 8.3.3) {@code status='SHIPPED'}이고
 * {@code shipped_at}이 N일[정책값, 초안 3영업일] 이상 경과한 건을 {@code DELIVERED}로 전이한다.
 *
 * <p>[단순화] "영업일" 계산(주말·공휴일 제외)은 이번 목업 배치 범위에서 달력일로 단순화했다 —
 * 어차피 정책값 자체가 확정 전(fulfillment-service CLAUDE.md "정책값 확인 필요")이고, 실제
 * 택배사 연동 시 이 배치 전체가 웹훅 수신 로직으로 교체될 자리라 정교한 영업일 계산기를 지금
 * 만들 실익이 적다.
 */
@Component
public class ShipmentDeliveryMockScheduler {

    private static final Logger log = LoggerFactory.getLogger(ShipmentDeliveryMockScheduler.class);

    private final ShipmentRepository shipmentRepository;
    private final ShipmentDeliveryMockProcessor processor;
    private final long shippedToDeliveredDays;

    public ShipmentDeliveryMockScheduler(ShipmentRepository shipmentRepository,
                                          ShipmentDeliveryMockProcessor processor,
                                          @Value("${fulfillment.shipped-to-delivered-days:3}") long shippedToDeliveredDays) {
        this.shipmentRepository = shipmentRepository;
        this.processor = processor;
        this.shippedToDeliveredDays = shippedToDeliveredDays;
    }

    @Scheduled(fixedDelayString = "${fulfillment.shipment-delivery-mock.interval-ms:3600000}")
    public void run() {
        Instant threshold = Instant.now().minus(shippedToDeliveredDays, ChronoUnit.DAYS);
        List<Shipment> targets = shipmentRepository.findByStatusAndShippedAtBefore(ShipmentStatus.SHIPPED, threshold);
        int processed = 0;
        for (Shipment shipment : targets) {
            try {
                if (processor.markDeliveredOne(shipment.getFundingId())) {
                    processed++;
                }
            } catch (RuntimeException e) {
                log.error("배송완료 목업 처리 실패 — 다음 배치에서 재시도. fundingId={}", shipment.getFundingId(), e);
            }
        }
        if (processed > 0) {
            log.info("배송완료 목업 처리 {}건을 완료했습니다.", processed);
        }
    }
}
