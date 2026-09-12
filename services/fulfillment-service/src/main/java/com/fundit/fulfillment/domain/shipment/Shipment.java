package com.fundit.fulfillment.domain.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — {@code PREPARING→SHIPPED→DELIVERED→
 * RECEIPT_CONFIRMED} 상태 전이 불변식이 있다. 판매자가 발송 등록(FULFILLMENT-006)을 하기 전까지는
 * 이 애그리거트 자체가 존재하지 않는다(지연 생성) — fulfillment-service CLAUDE.md 핵심 설계 결정.
 */
@Getter
@Builder(toBuilder = true)
public class Shipment {

    private final Long id;
    private final Long fundingId;
    private final Long projectId;
    private ShipmentStatus status;
    private String carrier;
    private String trackingNumber;
    private Instant shippedAt;
    private Instant deliveredAt;
    private Instant receiptConfirmedAt;
    private boolean receiptAutoConfirmed;
    private final Instant createdAt;
    private Instant updatedAt;

    /** FULFILLMENT-006 — 발송정보 등록 시 shipments 행 자체가 없으면 PREPARING으로 새로 만든다. */
    public static Shipment create(Long fundingId, Long projectId) {
        return Shipment.builder()
                .fundingId(fundingId)
                .projectId(projectId)
                .status(ShipmentStatus.PREPARING)
                .receiptAutoConfirmed(false)
                .build();
    }

    /** FULFILLMENT-006 — 택배사·운송장 등록과 동시에 발송 완료 상태로 전환한다(목업, 실연동 없음). */
    public void registerShipment(String carrier, String trackingNumber) {
        if (status.ordinal() >= ShipmentStatus.SHIPPED.ordinal()) {
            throw new BusinessException(FulfillmentErrorCode.ALREADY_SHIPPED);
        }
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.status = ShipmentStatus.SHIPPED;
        this.shippedAt = Instant.now();
    }

    /** FULFILLMENT-007 — 배송완료 목업 배치 전용. */
    public void markDelivered(Instant at) {
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = at;
    }

    /**
     * FULFILLMENT-009/010 — 수령 확인(구매자 직접 또는 자동확정 배치). 이미 확정된 건은
     * idempotent하게 무시하고, 배송완료 전이면 예외를 던진다.
     */
    public void confirmReceipt(Instant at, boolean auto) {
        if (status == ShipmentStatus.RECEIPT_CONFIRMED) {
            return;
        }
        if (status != ShipmentStatus.DELIVERED) {
            throw new BusinessException(FulfillmentErrorCode.NOT_YET_DELIVERED);
        }
        this.status = ShipmentStatus.RECEIPT_CONFIRMED;
        this.receiptConfirmedAt = at;
        this.receiptAutoConfirmed = auto;
    }

    public boolean canConfirmReceipt() {
        return status == ShipmentStatus.DELIVERED;
    }
}
