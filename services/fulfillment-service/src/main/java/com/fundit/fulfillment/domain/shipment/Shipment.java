package com.fundit.fulfillment.domain.shipment;

import com.fundit.common.error.BusinessException;
import com.fundit.fulfillment.domain.FulfillmentErrorCode;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 복잡한 애그리거트(persistence-convention.md 기준) — {@code PREPARING→SHIPPED→DELIVERED→
 * RECEIPT_CONFIRMED} 상태 전이 불변식이 있다. 판매자가 발송 등록이나 임시저장(FULFILLMENT-006)을
 * 하기 전까지는 이 애그리거트 자체가 존재하지 않는다(지연 생성).
 *
 * <p>행이 있다고 발송된 것은 아니다 — 임시저장으로 {@code PREPARING} 행이 먼저 생길 수 있어,
 * 발송 여부는 반드시 {@code status}로 판정한다(행 존재로 판정하지 말 것).
 */
@Getter
@Builder(toBuilder = true)
public class Shipment {

    private final Long id;
    private final UUID fundingId;
    private final UUID projectId;
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
    public static Shipment create(UUID fundingId, UUID projectId) {
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

    /**
     * FULFILLMENT-006 — 발송 전 택배사·운송장 임시저장. 상태 전이도, 이벤트 발행도 없다
     * (실제 발송 처리는 {@link #registerShipment}).
     */
    public void saveShippingInfo(String carrier, String trackingNumber) {
        if (status.ordinal() >= ShipmentStatus.SHIPPED.ordinal()) {
            throw new BusinessException(FulfillmentErrorCode.ALREADY_SHIPPED);
        }
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
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
